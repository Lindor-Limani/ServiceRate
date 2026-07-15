package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.StripeOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeConnectService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final MailService mailService;
    private final UserService userService;
    private final StripeWebhookEventService stripeWebhookEventService;

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.connect.country:AT}")
    private String connectCountry;

    @Value("${stripe.refresh-url:${APP_FRONTEND_BASE_URL:http://localhost:5500}/provider-dashboard.html?stripe=refresh}")
    private String refreshUrl;

    @Value("${stripe.return-url:${APP_FRONTEND_BASE_URL:http://localhost:5500}/provider-dashboard.html?stripe=return}")
    private String returnUrl;

    @Value("${stripe.checkout-success-url:${APP_FRONTEND_BASE_URL:http://localhost:5500}/customer-app.html?stripe=success&session_id={CHECKOUT_SESSION_ID}}")
    private String checkoutSuccessUrl;

    @Value("${stripe.checkout-cancel-url:${APP_FRONTEND_BASE_URL:http://localhost:5500}/customer-app.html?stripe=cancel}")
    private String checkoutCancelUrl;

    @Transactional
    public StripeOnboardingLinkResponse createOnboardingLink(String providerEmail) {
        configure();
        User provider = providerByEmail(providerEmail);
        requireProvider(provider);
        try {
            if (provider.getStripeConnectedAccountId() == null || provider.getStripeConnectedAccountId().isBlank()) {
                Account account = Account.create(AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setCountry(connectCountry)
                        .setEmail(provider.getEmail())
                        .setCapabilities(AccountCreateParams.Capabilities.builder()
                                .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                .build())
                        .build());
                provider.setStripeConnectedAccountId(account.getId());
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(provider.getStripeConnectedAccountId())
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            provider.setStripeOnboardingStatus("ONBOARDING_STARTED");
            userRepository.save(provider);
            return new StripeOnboardingLinkResponse(provider.getStripeConnectedAccountId(), link.getUrl());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe-Onboarding konnte nicht gestartet werden: " + e.getMessage(), e);
        }
    }

    @Transactional
    public UserResponse refreshOnboardingStatus(String providerEmail) {
        configure();
        User provider = providerByEmail(providerEmail);
        requireProvider(provider);
        refreshProviderStatus(provider);
        return userService.getById(provider.getId());
    }

    @Transactional
    public StripeCheckout createCheckoutSession(Booking booking, boolean savePaymentMethod) {
        configure();
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Stripe Checkout benötigt eine persistierte Buchung.");
        }
        User customer = booking.getCustomer();
        User provider = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (!isProviderStripeAvailable(provider)) {
            throw new IllegalArgumentException("Dieser Anbieter hat Stripe Connect noch nicht fertig eingerichtet.");
        }
        applyStripeCheckoutSnapshot(booking, provider);

        try {
            String customerId = ensureCustomer(customer);
            long amount = booking.getStripeExpectedAmountMinor();
            long fee = booking.getStripeExpectedApplicationFeeMinor();
            String description = serviceTitle(booking.getServiceOffering());

            SessionCreateParams.PaymentIntentData.Builder paymentIntent = SessionCreateParams.PaymentIntentData.builder()
                    .setApplicationFeeAmount(fee)
                    .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                            .setDestination(booking.getStripeConnectedAccountId())
                            .build())
                    .putMetadata("booking_id", booking.getId().toString())
                    .putMetadata("provider_id", provider.getId().toString());
            if (savePaymentMethod) {
                paymentIntent.setSetupFutureUsage(SessionCreateParams.PaymentIntentData.SetupFutureUsage.OFF_SESSION);
            }

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(checkoutSuccessUrl)
                    .setCancelUrl(checkoutCancelUrl)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setPaymentIntentData(paymentIntent.build())
                    .putMetadata("booking_id", booking.getId().toString())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(booking.getStripeCurrencyCode().toLowerCase(Locale.ROOT))
                                    .setUnitAmount(amount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(description)
                                            .build())
                                    .build())
                            .build())
                    .build();
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(checkoutIdempotencyKey(booking.getId()))
                    .build();
            Session session = Session.create(params, requestOptions);

            booking.setStripeCustomerId(customerId);
            booking.setStripeCheckoutSessionId(session.getId());
            booking.setStripePaymentIntentId(session.getPaymentIntent());
            booking.setCheckoutUrl(session.getUrl());
            booking.setPaymentProvider("CARD");
            booking.setPaymentStatus("CHECKOUT_CREATED");
            booking.setSettlementStatus("STRIPE_DESTINATION_CHARGE_PENDING");
            booking.setPaymentNote(savePaymentMethod
                    ? "Stripe Checkout wurde erstellt. Die Karte darf fuer spaetere Buchungen bei Stripe gespeichert werden."
                    : "Stripe Checkout wurde erstellt. Kartendaten werden nur bei Stripe verarbeitet.");
            bookingRepository.save(booking);
            return new StripeCheckout(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe Checkout konnte nicht erstellt werden: " + e.getMessage(), e);
        }
    }

    public void handleWebhook(String payload, String signatureHeader) {
        configure();
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Ungueltige Stripe Webhook-Signatur.");
        }

        try {
            stripeWebhookEventService.processOnce(event.getId(), event.getType(), () -> processWebhookEvent(event));
        } catch (DuplicateStripeWebhookEventException ignored) {
            // Stripe erwartet auch für ein bereits erfolgreich verarbeitetes Event HTTP 2xx.
        }
    }

    private void processWebhookEvent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Object stripeObject = deserializer.getObject().orElse(null);
        switch (event.getType()) {
            case "checkout.session.completed" -> {
                if (!(stripeObject instanceof Session session)) {
                    throw new IllegalArgumentException("Stripe Checkout Event enthaelt keine verwertbaren Daten.");
                }
                markCheckoutCompleted(session);
            }
            case "payment_intent.payment_failed" -> {
                if (!(stripeObject instanceof PaymentIntent intent)) {
                    throw new IllegalArgumentException("Stripe Payment Event enthaelt keine verwertbaren Daten.");
                }
                markPaymentFailed(intent);
            }
            case "account.updated" -> {
                if (!(stripeObject instanceof Account account)) {
                    throw new IllegalArgumentException("Stripe Account Event enthaelt keine verwertbaren Daten.");
                }
                updateAccountStatus(account);
            }
            default -> {
                // Signierte, aber von dieser Anwendung nicht verwendete Eventtypen werden quittiert.
            }
        }
    }

    public boolean isProviderStripeAvailable(User provider) {
        return provider != null
                && provider.getStripeConnectedAccountId() != null
                && !provider.getStripeConnectedAccountId().isBlank()
                && "CONNECTED".equals(provider.getStripeOnboardingStatus());
    }

    private void markCheckoutCompleted(Session session) {
        UUID bookingId = bookingIdFrom(session.getMetadata());
        Booking booking = bookingRepository.findByIdForStateTransition(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Buchung aus Stripe Webhook nicht gefunden."));
        requireCardPayment(booking);
        requireMatchingStripeId(
                booking.getStripeCheckoutSessionId(), session.getId(), "Checkout-Session-ID"
        );
        String paymentIntentId = requireStripeId(session.getPaymentIntent(), "Payment-Intent-ID");
        if (booking.getStripePaymentIntentId() != null && !booking.getStripePaymentIntentId().isBlank()) {
            requireMatchingStripeId(
                    booking.getStripePaymentIntentId(), paymentIntentId, "Payment-Intent-ID"
            );
        }
        requireCompleteStripeCheckoutSnapshot(booking);
        requireMatchingStripeAmount(
                booking.getStripeExpectedAmountMinor(), session.getAmountTotal(), "Checkout-Gesamtbetrag"
        );
        requireMatchingStripeCurrency(booking.getStripeCurrencyCode(), session.getCurrency(), "Checkout-Waehrung");
        if (!"paid".equals(session.getPaymentStatus())) {
            throw new IllegalArgumentException("Stripe Checkout Event bestaetigt keine bezahlte Session.");
        }

        PaymentIntent intent = requireVerifiedPaymentIntent(session, paymentIntentId);
        requireMatchingStripeAmount(
                booking.getStripeExpectedAmountMinor(), intent.getAmount(), "PaymentIntent-Betrag"
        );
        requireMatchingStripeAmount(
                booking.getStripeExpectedAmountMinor(), intent.getAmountReceived(), "empfangenen PaymentIntent-Betrag"
        );
        requireMatchingStripeApplicationFee(
                booking.getStripeExpectedApplicationFeeMinor(), intent.getApplicationFeeAmount()
        );
        requireMatchingStripeCurrency(
                booking.getStripeCurrencyCode(), intent.getCurrency(), "PaymentIntent-Waehrung"
        );
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalArgumentException("Stripe PaymentIntent ist nicht erfolgreich abgeschlossen.");
        }
        String destination = intent.getTransferData() == null
                ? null
                : intent.getTransferData().getDestination();
        requireMatchingStripeId(
                booking.getStripeConnectedAccountId(), destination, "Connected-Account-ID"
        );

        if ("PAID".equals(booking.getPaymentStatus())) {
            return;
        }
        if (!"CHECKOUT_CREATED".equals(booking.getPaymentStatus())
                && !"FAILED".equals(booking.getPaymentStatus())) {
            throw new ConflictException("Stripe Checkout kann aus diesem Zahlungsstatus nicht abgeschlossen werden.");
        }

        booking.setStripePaymentIntentId(paymentIntentId);
        booking.setPaymentStatus("PAID");
        booking.setSettlementStatus("STRIPE_DESTINATION_CHARGE_COMPLETED");
        booking.setPaymentNote("Kartenzahlung erfolgreich ueber Stripe abgeschlossen.");
        booking.setSettlementNote("Stripe hat die Plattformgebuehr einbehalten und den Provider-Anteil an den Connected Account transferiert.");
        booking.setPaidAt(OffsetDateTime.now());

        booking.setStripePaymentMethodId(intent.getPaymentMethod());
        if (booking.getCustomer() != null && booking.getCustomer().getStripeDefaultPaymentMethodId() == null) {
            booking.getCustomer().setStripeDefaultPaymentMethodId(intent.getPaymentMethod());
        }

        Booking saved = bookingRepository.saveAndFlush(booking);
        mailService.sendPaymentRecordedMail(saved);
    }

    private void markPaymentFailed(PaymentIntent intent) {
        UUID bookingId = bookingIdFrom(intent.getMetadata());
        Booking booking = bookingRepository.findByIdForStateTransition(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Buchung aus Stripe Webhook nicht gefunden."));
        requireCardPayment(booking);
        requireMatchingStripeId(
                booking.getStripePaymentIntentId(), intent.getId(), "Payment-Intent-ID"
        );

        if ("PAID".equals(booking.getPaymentStatus()) || "FAILED".equals(booking.getPaymentStatus())) {
            return;
        }
        if (!"CHECKOUT_CREATED".equals(booking.getPaymentStatus())) {
            throw new ConflictException("Stripe-Zahlung kann aus diesem Zahlungsstatus nicht fehlschlagen.");
        }

        booking.setPaymentStatus("FAILED");
        booking.setPaymentNote("Stripe-Kartenzahlung ist fehlgeschlagen.");
        bookingRepository.saveAndFlush(booking);
    }

    private void updateAccountStatus(Account account) {
        userRepository.findByStripeConnectedAccountId(account.getId())
                .ifPresent(user -> {
                    user.setStripeOnboardingStatus(stripeStatus(account));
                    userRepository.save(user);
                });
    }

    private String ensureCustomer(User user) throws StripeException {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        Customer customer = Customer.create(CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(fullName(user))
                .build());
        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);
        return customer.getId();
    }

    private void refreshProviderStatus(User provider) {
        if (provider.getStripeConnectedAccountId() == null || provider.getStripeConnectedAccountId().isBlank()) {
            provider.setStripeOnboardingStatus("NOT_CONNECTED");
            userRepository.save(provider);
            return;
        }
        try {
            Account account = Account.retrieve(provider.getStripeConnectedAccountId());
            provider.setStripeOnboardingStatus(stripeStatus(account));
            userRepository.save(provider);
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe-Status konnte nicht geprueft werden: " + e.getMessage(), e);
        }
    }

    private String stripeStatus(Account account) {
        if (Boolean.TRUE.equals(account.getChargesEnabled()) && Boolean.TRUE.equals(account.getPayoutsEnabled())) {
            return "CONNECTED";
        }
        if (account.getRequirements() != null && account.getRequirements().getCurrentlyDue() != null
                && !account.getRequirements().getCurrentlyDue().isEmpty()) {
            return "ACTION_REQUIRED";
        }
        return "ONBOARDING_STARTED";
    }

    private User providerByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
    }

    private void requireProvider(User provider) {
        if (provider == null || !"PROVIDER".equals(provider.getAccountType())) {
            throw new IllegalArgumentException("Diese Aktion ist nur fuer Provider erlaubt.");
        }
    }

    private UUID bookingIdFrom(Map<String, String> metadata) {
        if (metadata == null || metadata.get("booking_id") == null) {
            throw new IllegalArgumentException("Stripe Event enthaelt keine booking_id.");
        }
        return UUID.fromString(metadata.get("booking_id"));
    }

    private void requireCardPayment(Booking booking) {
        if (!"CARD".equals(booking.getPaymentProvider())) {
            throw new IllegalArgumentException("Stripe Event gehoert nicht zu einer Kartenzahlung.");
        }
    }

    private void requireMatchingStripeId(String storedId, String eventId, String label) {
        String validatedStoredId = requireStripeId(storedId, "gespeicherte " + label);
        String validatedEventId = requireStripeId(eventId, label);
        if (!validatedStoredId.equals(validatedEventId)) {
            throw new IllegalArgumentException("Stripe Event stimmt nicht mit der gespeicherten " + label + " ueberein.");
        }
    }

    private String requireStripeId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Stripe Event enthaelt keine gueltige " + label + ".");
        }
        return value;
    }

    private void applyStripeCheckoutSnapshot(Booking booking, User provider) {
        boolean snapshotStarted = booking.getStripeExpectedAmountMinor() != null
                || booking.getStripeExpectedApplicationFeeMinor() != null
                || booking.getStripeCurrencyCode() != null
                || booking.getStripeConnectedAccountId() != null;
        if (snapshotStarted) {
            requireCompleteStripeCheckoutSnapshot(booking);
            if (!booking.getStripeConnectedAccountId().equals(provider.getStripeConnectedAccountId().trim())) {
                throw new IllegalArgumentException(
                        "Der gespeicherte Stripe Connected Account entspricht nicht dem verifizierten Anbieter."
                );
            }
            return;
        }

        long expectedAmountMinor = cents(booking.getGrossAmount());
        if (expectedAmountMinor <= 0) {
            throw new IllegalArgumentException("Stripe Checkout erfordert einen positiven Buchungsbetrag.");
        }
        long expectedApplicationFeeMinor = cents(booking.getPlatformFeeAmount());
        if (expectedApplicationFeeMinor < 0 || expectedApplicationFeeMinor > expectedAmountMinor) {
            throw new IllegalArgumentException(
                    "Stripe Checkout erfordert eine Plattformgebuehr zwischen null und dem Buchungsbetrag."
            );
        }
        String currencyCode = booking.getBookingCurrencyCode();
        try {
            Currency.getInstance(currencyCode);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Stripe Checkout erfordert einen gueltigen ISO-Waehrungscode.");
        }
        booking.setStripeExpectedAmountMinor(expectedAmountMinor);
        booking.setStripeExpectedApplicationFeeMinor(expectedApplicationFeeMinor);
        booking.setStripeCurrencyCode(currencyCode);
        booking.setStripeConnectedAccountId(provider.getStripeConnectedAccountId().trim());
    }

    private void requireCompleteStripeCheckoutSnapshot(Booking booking) {
        if (booking.getStripeExpectedAmountMinor() == null
                || booking.getStripeExpectedAmountMinor() <= 0
                || booking.getStripeExpectedApplicationFeeMinor() == null
                || booking.getStripeExpectedApplicationFeeMinor() < 0
                || booking.getStripeExpectedApplicationFeeMinor() > booking.getStripeExpectedAmountMinor()
                || booking.getStripeCurrencyCode() == null
                || !booking.getStripeCurrencyCode().matches("[A-Z]{3}")
                || booking.getStripeConnectedAccountId() == null
                || booking.getStripeConnectedAccountId().isBlank()
                || !booking.getStripeConnectedAccountId().equals(booking.getStripeConnectedAccountId().trim())) {
            throw new ConflictException(
                    "Der Stripe-Checkout enthaelt keine vollstaendigen unveraenderlichen Zahlungs-Sollwerte."
            );
        }
    }

    private PaymentIntent requireVerifiedPaymentIntent(Session session, String paymentIntentId) {
        PaymentIntent intent = session.getPaymentIntentObject();
        if (intent == null) {
            try {
                intent = PaymentIntent.retrieve(
                        paymentIntentId,
                        PaymentIntentRetrieveParams.builder().addExpand("payment_method").build(),
                        null
                );
            } catch (StripeException e) {
                throw new IllegalStateException("Stripe PaymentIntent konnte nicht verifiziert werden.", e);
            }
        }
        if (intent == null) {
            throw new IllegalStateException("Stripe PaymentIntent konnte nicht verifiziert werden.");
        }
        requireMatchingStripeId(paymentIntentId, intent.getId(), "Payment-Intent-ID");
        return intent;
    }

    private void requireMatchingStripeAmount(Long expected, Long actual, String label) {
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new IllegalArgumentException("Stripe Event stimmt nicht mit dem erwarteten " + label + " ueberein.");
        }
    }

    private void requireMatchingStripeApplicationFee(Long expected, Long actual) {
        long actualFee = actual == null ? 0L : actual;
        if (expected == null || expected != actualFee) {
            throw new IllegalArgumentException(
                    "Stripe Event stimmt nicht mit der erwarteten PaymentIntent-Plattformgebuehr ueberein."
            );
        }
    }

    private void requireMatchingStripeCurrency(String expected, String actual, String label) {
        String normalizedActual = actual == null ? null : actual.toUpperCase(Locale.ROOT);
        if (expected == null || !expected.equals(normalizedActual)) {
            throw new IllegalArgumentException("Stripe Event stimmt nicht mit der erwarteten " + label + " ueberein.");
        }
    }

    private long cents(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Stripe Checkout erfordert Geldbeträge mit höchstens zwei Nachkommastellen im unterstützten Wertebereich.");
        }
    }

    private String serviceTitle(ServiceOffering offering) {
        return offering == null || offering.getTitle() == null || offering.getTitle().isBlank()
                ? "ServiceRate Buchung"
                : offering.getTitle();
    }

    private String fullName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private String checkoutIdempotencyKey(UUID bookingId) {
        return "servicerate-checkout-" + bookingId;
    }

    private void configure() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe ist nicht konfiguriert. Bitte STRIPE_SECRET_KEY setzen.");
        }
        Stripe.apiKey = secretKey;
    }

    public record StripeCheckout(String sessionId, String url) {}
}
