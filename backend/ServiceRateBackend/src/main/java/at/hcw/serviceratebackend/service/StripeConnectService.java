package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.StripeOnboardingLinkResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
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

    @Value("${stripe.currency:eur}")
    private String currency;

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
        User customer = booking.getCustomer();
        User provider = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (!isProviderStripeAvailable(provider)) {
            throw new IllegalArgumentException("Dieser Anbieter hat Stripe Connect noch nicht fertig eingerichtet.");
        }

        try {
            String customerId = ensureCustomer(customer);
            long amount = cents(booking.getGrossAmount());
            long fee = cents(booking.getPlatformFeeAmount());
            String description = serviceTitle(booking.getServiceOffering());

            SessionCreateParams.PaymentIntentData.Builder paymentIntent = SessionCreateParams.PaymentIntentData.builder()
                    .setApplicationFeeAmount(fee)
                    .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                            .setDestination(provider.getStripeConnectedAccountId())
                            .build())
                    .putMetadata("booking_id", booking.getId().toString())
                    .putMetadata("provider_id", provider.getId().toString());
            if (savePaymentMethod) {
                paymentIntent.setSetupFutureUsage(SessionCreateParams.PaymentIntentData.SetupFutureUsage.OFF_SESSION);
            }

            Session session = Session.create(SessionCreateParams.builder()
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
                                    .setCurrency(currency.toLowerCase(Locale.ROOT))
                                    .setUnitAmount(amount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(description)
                                            .build())
                                    .build())
                            .build())
                    .build());

            booking.setStripeCustomerId(customerId);
            booking.setStripeConnectedAccountId(provider.getStripeConnectedAccountId());
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Buchung aus Stripe Webhook nicht gefunden."));
        booking.setStripeCheckoutSessionId(session.getId());
        booking.setStripePaymentIntentId(session.getPaymentIntent());
        booking.setPaymentStatus("PAID");
        booking.setPaymentProvider("CARD");
        booking.setSettlementStatus("STRIPE_DESTINATION_CHARGE_COMPLETED");
        booking.setPaymentNote("Kartenzahlung erfolgreich ueber Stripe abgeschlossen.");
        booking.setSettlementNote("Stripe hat die Plattformgebuehr einbehalten und den Provider-Anteil an den Connected Account transferiert.");
        booking.setPaidAt(OffsetDateTime.now());

        if (session.getPaymentIntent() != null && !session.getPaymentIntent().isBlank()) {
            try {
                PaymentIntent intent = PaymentIntent.retrieve(session.getPaymentIntent(),
                        PaymentIntentRetrieveParams.builder().addExpand("payment_method").build(),
                        null);
                booking.setStripePaymentMethodId(intent.getPaymentMethod());
                if (booking.getCustomer() != null && booking.getCustomer().getStripeDefaultPaymentMethodId() == null) {
                    booking.getCustomer().setStripeDefaultPaymentMethodId(intent.getPaymentMethod());
                }
            } catch (StripeException ignored) {
                // Payment is already confirmed by the webhook; missing method ID should not undo fulfilment.
            }
        }

        Booking saved = bookingRepository.save(booking);
        mailService.sendPaymentRecordedMail(saved);
    }

    private void markPaymentFailed(PaymentIntent intent) {
        UUID bookingId = bookingIdFrom(intent.getMetadata());
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            booking.setPaymentStatus("FAILED");
            booking.setPaymentNote("Stripe-Kartenzahlung ist fehlgeschlagen.");
            bookingRepository.save(booking);
        });
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

    private long cents(Double value) {
        BigDecimal amount = BigDecimal.valueOf(value == null ? 0.0 : value)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
        return amount.longValueExact();
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

    private void configure() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe ist nicht konfiguriert. Bitte STRIPE_SECRET_KEY setzen.");
        }
        Stripe.apiKey = secretKey;
    }

    public record StripeCheckout(String sessionId, String url) {}
}
