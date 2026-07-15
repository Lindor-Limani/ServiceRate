package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.dto.CreateTimeEntryRequest;
import at.hcw.serviceratebackend.dto.PublishDeliveryRequest;
import at.hcw.serviceratebackend.dto.RecordPaymentRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.dto.TimeEntryResponse;
import at.hcw.serviceratebackend.dto.UpdateBookingWorkRequest;
import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.TimeEntry;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.TimeEntryRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final TimeEntryRepository timeEntryRepository;
    private final MailService mailService;
    private final PayPalService payPalService;
    private final StripeConnectService stripeConnectService;

    @Value("${app.platform-fee-percent:10}")
    private double platformFeePercent;

    @Value("${app.platform-fee-fixed:0}")
    private double platformFeeFixed;

    @Value("${app.backend-base-url:http://localhost:8081}")
    private String backendBaseUrl;

    public BookingResponse createBooking(CreateBookingRequest request, String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));

        requireAccountType(customer, "CUSTOMER");
        requireVerifiedEmail(customer);

        ServiceOffering service = serviceRepository.findById(request.serviceOfferingId())
                .orElseThrow(() -> new RuntimeException("Service nicht gefunden"));
        if (request.bookingDate() == null || request.bookingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Termine in der Vergangenheit können nicht gebucht werden.");
        }

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(service);
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setBookingDate(request.bookingDate()); // vom Kunden gewählter Wunschtermin
        booking.setStatus(BookingStatus.PENDING.name());
        applyBookingFinancialSnapshot(booking, service);

        Booking saved = bookingRepository.save(booking);
        mailService.sendBookingCreatedMail(saved);

        return toResponse(saved, null, null);
    }

    private java.util.List<BookingResponse> getBookingsForProvider(UUID providerId) {
        List<Booking> bookings = bookingRepository.findByServiceOffering_Provider_Id(providerId);
        Map<UUID, ReviewResponse> reviews = loadReviewsByBookingId(bookings);

        return bookings.stream()
                .map(b -> toResponse(b, fullName(b.getCustomer()), reviews.get(b.getId())))
                .toList();
    }

    public java.util.List<BookingResponse> getBookingsForProviderEmail(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        return getBookingsForProvider(provider.getId());
    }

    // Ändert den Status einer Buchung
    public BookingResponse updateBookingStatus(UUID bookingId, String newStatus, String providerEmail) {
        Booking booking = bookingRepository.findByIdForStateTransition(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        BookingStatus targetStatus = parseStatus(newStatus);
        BookingStatus sourceStatus = parsePersistedStatus(booking.getStatus());
        if (!isAllowedProviderTransition(sourceStatus, targetStatus)) {
            throw new ConflictException("Statuswechsel ist für den aktuellen Buchungsstatus nicht erlaubt.");
        }

        booking.setStatus(targetStatus.name());
        Booking saved = bookingRepository.save(booking);
        mailService.sendBookingStatusMail(saved);

        return toResponse(saved, null, findReviewResponse(saved));
    }

    public BookingResponse updateWorkLog(UUID bookingId, UpdateBookingWorkRequest request, String providerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        if (request.actualHours() != null && request.actualHours() < 0) {
            throw new IllegalArgumentException("Stunden dürfen nicht negativ sein.");
        }

        booking.setActualHours(request.actualHours());
        booking.setProviderNotes(trimOrNull(request.providerNotes()));
        return toResponse(bookingRepository.save(booking), fullName(booking.getCustomer()), findReviewResponse(booking));
    }

    public BookingResponse addTimeEntry(UUID bookingId, CreateTimeEntryRequest request, String providerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        if (request.hours() == null || request.hours() <= 0) {
            throw new IllegalArgumentException("Bitte positive Stunden angeben.");
        }

        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        entry.setBooking(booking);
        entry.setProvider(provider);
        entry.setWorkDate(request.workDate() == null ? java.time.LocalDate.now() : request.workDate());
        entry.setHours(request.hours());
        entry.setNote(trimOrNull(request.note()));
        timeEntryRepository.save(entry);

        double total = timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(bookingId).stream()
                .mapToDouble(TimeEntry::getHours)
                .sum();
        booking.setActualHours(total);
        bookingRepository.save(booking);
        return toResponse(booking, fullName(booking.getCustomer()), findReviewResponse(booking));
    }

    public BookingResponse publishDelivery(UUID bookingId, PublishDeliveryRequest request, String providerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        if (request.deliveryUrl() == null || request.deliveryUrl().isBlank()) {
            throw new IllegalArgumentException("Bitte einen Liefer-Link angeben.");
        }

        int hours = request.expiresInHours() == null ? 72 : Math.max(1, Math.min(request.expiresInHours(), 24 * 14));
        booking.setDeliveryUrl(request.deliveryUrl().trim());
        booking.setDeliveryLabel(trimOrNull(request.deliveryLabel()));
        booking.setDeliveryExpiresAt(OffsetDateTime.now().plusHours(hours));
        Booking saved = bookingRepository.save(booking);
        mailService.sendDeliveryPublishedMail(saved);
        return toResponse(saved, fullName(saved.getCustomer()), findReviewResponse(saved));
    }

    public BookingResponse createCheckout(UUID bookingId, CreateCheckoutRequest request, String customerEmail) {
        Booking booking = bookingRepository.findByIdForStateTransition(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Kunden.");
        }
        requireAcceptedForCheckout(booking);
        requireCompleteBookingFinancialSnapshot(booking);

        String provider = request.provider() == null || request.provider().isBlank()
                ? "MANUAL"
                : request.provider().trim().toUpperCase();
        if ("CARD".equals(provider)) {
            BookingResponse existingCheckout = existingStripeCheckout(booking);
            if (existingCheckout != null) {
                return existingCheckout;
            }
            requireStripeCheckoutMayStart(booking);
        } else if ("PAYPAL".equals(provider)) {
            BookingResponse existingCheckout = existingPayPalCheckout(booking);
            if (existingCheckout != null) {
                return existingCheckout;
            }
            requirePayPalCheckoutMayStart(booking);
        }
        applyMarketplaceAmounts(booking);
        booking.setPaymentProvider(provider);
        if ("PAYPAL".equals(provider)) {
            requireProviderPayPalAccount(booking);
            applyPayPalCheckoutSnapshot(booking);
            booking.setPaymentStatus("CHECKOUT_CREATED");
            PayPalService.PayPalOrder order = payPalService.createOrder(booking);
            booking.setPaypalOrderId(order.orderId());
            booking.setPaypalCaptureId(null);
            booking.setCheckoutUrl(order.approveUrl());
            booking.setSettlementStatus("PAYPAL_PLATFORM_FEE_PENDING");
            booking.setPaymentNote("PayPal Marketplace Order wurde erstellt. Provider ist Payee, Plattformgebühr wird separat ausgewiesen.");
        } else if ("CARD".equals(provider)) {
            stripeConnectService.createCheckoutSession(booking, Boolean.TRUE.equals(request.savePaymentMethod()));
        } else if ("CASH".equals(provider) || "BANK_TRANSFER".equals(provider)) {
            booking.setPaymentStatus("AWAITING_OFFLINE_PAYMENT");
            booking.setCheckoutUrl(null);
            booking.setSettlementStatus("NOT_READY");
            booking.setPaymentNote("Kunde zahlt direkt an den Provider. Provider muss Zahlungseingang danach im Dashboard verbuchen.");
            booking.setSettlementNote("Nach Zahlungseingang schuldet der Provider die Plattformprovision.");
        } else {
            booking.setPaymentStatus("CHECKOUT_CREATED");
            booking.setCheckoutUrl("/checkout.html?bookingId=" + booking.getId());
            booking.setSettlementStatus("PLATFORM_COLLECTED_PENDING_PROVIDER_PAYOUT");
            booking.setSettlementNote("Demo-Zahlungsart: Plattform kassiert und muss Provider-Netto auszahlen.");
        }
        return toResponse(bookingRepository.save(booking), providerName(booking.getServiceOffering()), findReviewResponse(booking));
    }

    public BookingResponse capturePayPalPayment(UUID bookingId, String orderId, String customerEmail) {
        Booking booking = bookingRepository.findByIdForStateTransition(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Kunden.");
        }
        if (!"PAYPAL".equals(booking.getPaymentProvider())) {
            throw new IllegalArgumentException("Diese Buchung wurde nicht mit PayPal gestartet.");
        }
        if (booking.getPaypalOrderId() == null || !booking.getPaypalOrderId().equals(orderId)) {
            throw new IllegalArgumentException("PayPal Order passt nicht zu dieser Buchung.");
        }
        if ("PAID".equals(booking.getPaymentStatus())) {
            return toResponse(booking, null, findReviewResponse(booking));
        }
        if (!"CHECKOUT_CREATED".equals(booking.getPaymentStatus())) {
            throw new ConflictException("PayPal-Capture ist nur für einen gestarteten Checkout möglich.");
        }
        requireCompletePayPalCheckoutSnapshot(booking);

        PayPalService.PayPalCapture capture = payPalService.captureOrder(booking.getId(), orderId);
        if (capture == null) {
            throw new IllegalStateException("PayPal Capture lieferte keine gültige Antwort.");
        }
        requirePayPalCaptureMatchesBooking(booking, orderId, capture);
        if (!"COMPLETED".equalsIgnoreCase(capture.status())) {
            throw new IllegalStateException("PayPal Zahlung wurde nicht abgeschlossen. Status: " + capture.status());
        }
        if (capture.captureId() == null || capture.captureId().isBlank()) {
            throw new IllegalStateException("PayPal Capture lieferte keine Capture-ID.");
        }

        booking.setPaymentStatus("PAID");
        booking.setPaymentNote("PayPal-Zahlung erfolgreich abgeschlossen.");
        booking.setPaypalCaptureId(capture.captureId());
        booking.setSettlementStatus("PAYPAL_SPLIT_COMPLETED");
        booking.setSettlementNote("PayPal hat die Zahlung beim Provider gecaptured; Plattformgebühr wurde im PayPal-Order-Request angegeben.");
        booking.setPaidAt(OffsetDateTime.now());
        Booking saved = bookingRepository.saveAndFlush(booking);
        mailService.sendPaymentRecordedMail(saved);
        return toResponse(saved, null, findReviewResponse(saved));
    }

    public BookingResponse recordProviderPayment(UUID bookingId, RecordPaymentRequest request, String providerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        String method = request.provider() == null || request.provider().isBlank()
                ? "CASH"
                : request.provider().trim().toUpperCase();
        if (!method.equals("CASH") && !method.equals("BANK_TRANSFER") && !method.equals("MANUAL") && !method.equals("PAYPAL") && !method.equals("CARD") && !method.equals("STRIPE") && !method.equals("SEPA")) {
            throw new IllegalArgumentException("Ungültige Zahlungsart.");
        }

        booking.setPaymentProvider(method);
        booking.setPaymentStatus("PAID");
        applyMarketplaceAmounts(booking);
        booking.setPaymentNote(trimOrNull(request.note()));
        if (method.equals("CASH") || method.equals("BANK_TRANSFER") || method.equals("MANUAL")) {
            booking.setSettlementStatus("PLATFORM_FEE_DUE_FROM_PROVIDER");
            booking.setSettlementNote("Provider hat die Zahlung direkt erhalten und muss die Plattformprovision begleichen.");
        } else {
            booking.setSettlementStatus("PLATFORM_COLLECTED_PENDING_PROVIDER_PAYOUT");
            booking.setSettlementNote("Zahlung wurde verbucht; Auszahlung/Abrechnung muss administrativ abgeschlossen werden.");
        }
        booking.setPaidAt(OffsetDateTime.now());
        Booking saved = bookingRepository.save(booking);
        mailService.sendPaymentRecordedMail(saved);
        return toResponse(saved, null, findReviewResponse(saved));
    }

    public String resolveDeliveryUrl(UUID bookingId, String email) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        UUID customerId = booking.getCustomer() != null ? booking.getCustomer().getId() : null;
        UUID providerId = booking.getServiceOffering() != null && booking.getServiceOffering().getProvider() != null
                ? booking.getServiceOffering().getProvider().getId()
                : null;

        boolean isCustomer = user.getId().equals(customerId);
        boolean isProvider = user.getId().equals(providerId);
        if (!isCustomer && !isProvider) {
            throw new IllegalArgumentException("Kein Zugriff auf diese Lieferung.");
        }
        if (isCustomer && !"PAID".equals(booking.getPaymentStatus())) {
            throw new IllegalArgumentException("Die Lieferung ist erst nach Zahlung verfügbar.");
        }
        if (booking.getDeliveryExpiresAt() != null && booking.getDeliveryExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Der Liefer-Link ist abgelaufen.");
        }
        if (booking.getDeliveryUrl() == null || booking.getDeliveryUrl().isBlank()) {
            throw new IllegalArgumentException("Für diese Buchung wurde noch keine Lieferung bereitgestellt.");
        }
        return booking.getDeliveryUrl();
    }

    private java.util.List<BookingResponse> getBookingsForCustomer(UUID customerId) {
        List<Booking> bookings = bookingRepository.findByCustomer_Id(customerId);
        Map<UUID, ReviewResponse> reviews = loadReviewsByBookingId(bookings);

        return bookings.stream()
                .map(b -> toResponse(b, providerName(b.getServiceOffering()), reviews.get(b.getId())))
                .toList();
    }

    public java.util.List<BookingResponse> getBookingsForCustomerEmail(String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        return getBookingsForCustomer(customer.getId());
    }

    private BookingResponse toResponse(Booking booking, String displayName, ReviewResponse review) {
        User customer = booking.getCustomer();
        User provider = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        return new BookingResponse(
                booking.getId(),
                fullName(customer),
                customer != null ? customer.getProfileImageUrl() : null,
                providerName(booking.getServiceOffering()),
                provider != null ? provider.getProfileImageUrl() : null,
                booking.getServiceOffering() != null ? booking.getServiceOffering().getId() : null,
                serviceTitle(booking.getServiceOffering()),
                bookedServicePrice(booking),
                serviceCategory(booking.getServiceOffering()),
                serviceImageUrl(booking.getServiceOffering()),
                serviceHasImage(booking.getServiceOffering()),
                booking.getStatus(),
                booking.getBookingDate(),
                booking.getActualHours(),
                booking.getProviderNotes(),
                booking.getCustomerNotes(),
                deliveryAccessPath(booking),
                booking.getDeliveryLabel(),
                booking.getDeliveryExpiresAt(),
                isDeliveryAvailable(booking),
                booking.getPaymentStatus(),
                booking.getCheckoutUrl(),
                booking.getPaymentProvider(),
                booking.getPaymentNote(),
                booking.getPaidAt(),
                booking.getPaypalOrderId(),
                booking.getPaypalCaptureId(),
                booking.getStripeCheckoutSessionId(),
                booking.getStripePaymentIntentId(),
                booking.getGrossAmount(),
                booking.getPlatformFeeAmount(),
                booking.getProviderReceivableAmount(),
                booking.getSettlementStatus(),
                booking.getSettlementNote(),
                isProviderPaypalAvailable(provider),
                stripeConnectService.isProviderStripeAvailable(provider),
                true,
                loadTimeEntries(booking.getId()),
                review
        );
    }

    private String serviceCategory(ServiceOffering service) {
        return service == null ? null : service.getCategory();
    }

    private String serviceImageUrl(ServiceOffering service) {
        if (service == null) {
            return null;
        }
        List<String> images = parseImageUrls(service.getImageUrls(), service.getImageUrl());
        if (images.isEmpty()) {
            return null;
        }
        String mediaUrl = blankToNull(images.get(0));
        if (mediaUrl == null) {
            return null;
        }
        if (mediaUrl.regionMatches(true, 0, "data:", 0, 5)) {
            return backendBaseUrl + "/api/services/" + service.getId() + "/image?v=" + Integer.toHexString(mediaUrl.hashCode());
        }
        return mediaUrl;
    }

    private boolean serviceHasImage(ServiceOffering service) {
        return service != null && !parseImageUrls(service.getImageUrls(), service.getImageUrl()).isEmpty();
    }

    private List<String> parseImageUrls(String imageUrls, String fallbackImageUrl) {
        List<String> parsed = imageUrls == null || imageUrls.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.stream(imageUrls.split("\\R"))
                        .map(this::blankToNull)
                        .filter(value -> value != null)
                        .limit(10)
                        .toList());
        String fallback = blankToNull(fallbackImageUrl);
        if (parsed.isEmpty() && fallback != null) {
            parsed.add(fallback);
        }
        return parsed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ReviewResponse findReviewResponse(Booking booking) {
        return reviewRepository.findByBookingId(booking.getId()).stream()
                .findFirst()
                .map(reviewService::toResponse)
                .orElse(null);
    }

    private Map<UUID, ReviewResponse> loadReviewsByBookingId(List<Booking> bookings) {
        List<UUID> bookingIds = bookings.stream()
                .map(Booking::getId)
                .toList();

        if (bookingIds.isEmpty()) {
            return Map.of();
        }

        return reviewRepository.findByBookingIdIn(bookingIds).stream()
                .map(reviewService::toResponse)
                .collect(Collectors.toMap(
                        ReviewResponse::bookingId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    // ── Null-sichere Helfer ────────────────────────────────────────────────────
    // Setzt Vor-/Nachname zusammen; fängt fehlenden User und null-Felder ab
    private String fullName(User user) {
        if (user == null) return "Unbekannter Nutzer";
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last  = user.getLastName()  != null ? user.getLastName()  : "";
        String name  = (first + " " + last).trim();
        return name.isEmpty() ? "Unbekannter Nutzer" : name;
    }

    // Name des Anbieters aus dem ServiceOffering; fängt gelöschten Service/Provider ab
    private String providerName(ServiceOffering offering) {
        if (offering == null) return "Unbekannter Anbieter";
        String name = fullName(offering.getProvider());
        return "Unbekannter Nutzer".equals(name) ? "Unbekannter Anbieter" : name;
    }

    // Titel des Service; fängt gelöschten Service / fehlenden Titel ab
    private String serviceTitle(ServiceOffering offering) {
        if (offering == null || offering.getTitle() == null) return "Unbekannter Service";
        return offering.getTitle();
    }

    private Double bookedServicePrice(Booking booking) {
        if (booking.getBookedUnitPrice() != null) {
            return booking.getBookedUnitPrice().doubleValue();
        }
        ServiceOffering offering = booking.getServiceOffering();
        return offering == null || offering.getPrice() == null ? 0.0 : offering.getPrice().doubleValue();
    }

    private void applyMarketplaceAmounts(Booking booking) {
        double gross = calculateGrossAmount(booking);
        double fee = roundMoney(gross * (platformFeePercent / 100.0) + platformFeeFixed);
        if (fee > gross) {
            fee = gross;
        }
        booking.setGrossAmount(gross);
        booking.setPlatformFeeAmount(fee);
        booking.setProviderReceivableAmount(roundMoney(gross - fee));
    }

    private double calculateGrossAmount(Booking booking) {
        requireCompleteBookingFinancialSnapshot(booking);
        Double actualHours = booking.getActualHours();
        if (actualHours != null && !Double.isFinite(actualHours)) {
            throw new IllegalArgumentException("Checkout erfordert eine endliche Stundenanzahl.");
        }
        BigDecimal hours = actualHours != null && actualHours > 0
                ? BigDecimal.valueOf(actualHours)
                : BigDecimal.ONE;
        return booking.getBookedUnitPrice()
                .multiply(hours)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void applyBookingFinancialSnapshot(Booking booking, ServiceOffering service) {
        BigDecimal price = service == null ? null : service.getPrice();
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Buchungen erfordern einen positiven Angebotspreis.");
        }
        BigDecimal bookedUnitPrice;
        try {
            bookedUnitPrice = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Angebotspreise dürfen höchstens zwei Nachkommastellen besitzen.");
        }
        if (bookedUnitPrice.precision() > 19) {
            throw new IllegalArgumentException("Der Angebotspreis überschreitet den unterstützten Wertebereich.");
        }

        String currencyCode = service.getCurrencyCode() == null
                ? ""
                : service.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!isIsoCurrencyCode(currencyCode)) {
            throw new IllegalArgumentException("Buchungen erfordern einen gültigen ISO-Währungscode.");
        }
        booking.setBookedUnitPrice(bookedUnitPrice);
        booking.setBookingCurrencyCode(currencyCode);
    }

    private void requireCompleteBookingFinancialSnapshot(Booking booking) {
        BigDecimal price = booking.getBookedUnitPrice();
        String currencyCode = booking.getBookingCurrencyCode();
        if (price == null
                || price.signum() <= 0
                || price.scale() > 2
                || price.precision() > 19
                || currencyCode == null
                || !isIsoCurrencyCode(currencyCode)
                || !currencyCode.equals(currencyCode.trim())) {
            throw new ConflictException(
                    "Die Buchung enthält keinen vollständigen unveränderlichen Preis- und Währungssnapshot."
            );
        }
    }

    private boolean isIsoCurrencyCode(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            return false;
        }
        try {
            Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültiger Buchungsstatus.");
        }
    }

    private void requireAccountType(User user, String expectedType) {
        if (user == null || !expectedType.equals(user.getAccountType())) {
            throw new IllegalArgumentException("Diese Aktion ist für diese Rolle nicht erlaubt.");
        }
    }

    private void requireVerifiedEmail(User user) {
        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Bitte verifiziere zuerst deine E-Mail-Adresse.");
        }
    }

    private void requireProviderOwnsBooking(User provider, Booking booking) {
        User owner = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (owner == null || !owner.getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Anbieter.");
        }
    }

    private void requireProviderPayPalAccount(Booking booking) {
        User provider = booking.getServiceOffering() != null ? booking.getServiceOffering().getProvider() : null;
        if (!payPalService.isProviderCheckoutEligible(provider)) {
            throw new IllegalArgumentException("PayPal-Checkout ist für diesen Anbieter nicht vollständig verifiziert.");
        }
    }

    private void requirePayPalCaptureMatchesBooking(
            Booking booking,
            String expectedOrderId,
            PayPalService.PayPalCapture capture
    ) {
        if (!expectedOrderId.equals(capture.orderId())) {
            throw new IllegalStateException("PayPal Capture ist nicht der erwarteten Order zugeordnet.");
        }
        String expectedBookingId = booking.getId().toString();
        if (!expectedBookingId.equals(capture.bookingReferenceId())) {
            throw new IllegalStateException("PayPal Capture enthält keine passende Buchungsreferenz.");
        }
        if (!expectedBookingId.equals(capture.bookingCustomId())) {
            throw new IllegalStateException("PayPal Capture enthält keine passende Buchungskennung.");
        }
        if (capture.amount() == null
                || booking.getPaypalExpectedAmount().compareTo(capture.amount()) != 0) {
            throw new IllegalStateException("PayPal Capture enthält nicht den erwarteten Betrag.");
        }
        if (!booking.getPaypalCurrencyCode().equals(capture.currencyCode())) {
            throw new IllegalStateException("PayPal Capture enthält nicht die erwartete Währung.");
        }
        if (!booking.getPaypalPayeeMerchantId().equals(capture.payeeMerchantId())) {
            throw new IllegalStateException("PayPal Capture enthält nicht den erwarteten Zahlungsempfänger.");
        }
    }

    private void applyPayPalCheckoutSnapshot(Booking booking) {
        ServiceOffering offering = booking.getServiceOffering();
        User provider = offering == null ? null : offering.getProvider();
        String merchantId = provider == null ? null : provider.getPaypalMerchantId();
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("PayPal-Checkout ist für diesen Anbieter nicht vollständig verifiziert.");
        }
        if (booking.getGrossAmount() == null || booking.getGrossAmount() <= 0) {
            throw new IllegalArgumentException("PayPal-Checkout erfordert einen positiven Buchungsbetrag.");
        }
        booking.setPaypalExpectedAmount(BigDecimal.valueOf(booking.getGrossAmount()).setScale(2, RoundingMode.HALF_UP));
        booking.setPaypalCurrencyCode(booking.getBookingCurrencyCode());
        booking.setPaypalPayeeMerchantId(merchantId.trim());
    }

    private void requireCompletePayPalCheckoutSnapshot(Booking booking) {
        if (!hasCompletePayPalCheckoutSnapshot(booking)) {
            throw new ConflictException(
                    "Der PayPal-Checkout enthält keine vollständigen unveränderlichen Zahlungs-Sollwerte."
            );
        }
    }

    private boolean hasCompletePayPalCheckoutSnapshot(Booking booking) {
        return booking.getPaypalExpectedAmount() != null
                && booking.getPaypalExpectedAmount().signum() > 0
                && booking.getPaypalExpectedAmount().scale() <= 2
                && booking.getPaypalCurrencyCode() != null
                && booking.getPaypalCurrencyCode().matches("[A-Z]{3}")
                && booking.getPaypalPayeeMerchantId() != null
                && !booking.getPaypalPayeeMerchantId().isBlank()
                && booking.getPaypalPayeeMerchantId().equals(booking.getPaypalPayeeMerchantId().trim());
    }

    private BookingResponse existingStripeCheckout(Booking booking) {
        boolean reusableStatus = "CHECKOUT_CREATED".equals(booking.getPaymentStatus())
                || "FAILED".equals(booking.getPaymentStatus());
        if (!"CARD".equals(booking.getPaymentProvider()) || !reusableStatus) {
            return null;
        }
        if (booking.getStripeCheckoutSessionId() == null || booking.getStripeCheckoutSessionId().isBlank()
                || booking.getCheckoutUrl() == null || booking.getCheckoutUrl().isBlank()
                || !hasCompleteStripeCheckoutSnapshot(booking)) {
            throw new ConflictException("Der vorhandene Stripe-Checkout ist unvollständig und kann nicht erneut verwendet werden.");
        }
        return toResponse(booking, providerName(booking.getServiceOffering()), findReviewResponse(booking));
    }

    private boolean hasCompleteStripeCheckoutSnapshot(Booking booking) {
        return booking.getStripeExpectedAmountMinor() != null
                && booking.getStripeExpectedAmountMinor() > 0
                && booking.getStripeExpectedApplicationFeeMinor() != null
                && booking.getStripeExpectedApplicationFeeMinor() >= 0
                && booking.getStripeExpectedApplicationFeeMinor() <= booking.getStripeExpectedAmountMinor()
                && booking.getStripeCurrencyCode() != null
                && booking.getStripeCurrencyCode().matches("[A-Z]{3}")
                && booking.getStripeConnectedAccountId() != null
                && !booking.getStripeConnectedAccountId().isBlank()
                && booking.getStripeConnectedAccountId().equals(booking.getStripeConnectedAccountId().trim());
    }

    private BookingResponse existingPayPalCheckout(Booking booking) {
        if (!"PAYPAL".equals(booking.getPaymentProvider())
                || !"CHECKOUT_CREATED".equals(booking.getPaymentStatus())) {
            return null;
        }
        if (booking.getPaypalOrderId() == null || booking.getPaypalOrderId().isBlank()
                || booking.getCheckoutUrl() == null || booking.getCheckoutUrl().isBlank()
                || !hasCompletePayPalCheckoutSnapshot(booking)) {
            throw new ConflictException("Der vorhandene PayPal-Checkout ist unvollständig und kann nicht erneut verwendet werden.");
        }
        return toResponse(booking, providerName(booking.getServiceOffering()), findReviewResponse(booking));
    }

    private void requireStripeCheckoutMayStart(Booking booking) {
        if (!"UNPAID".equals(booking.getPaymentStatus())) {
            throw new ConflictException("Für diese Buchung wurde bereits eine Zahlung gestartet.");
        }
        if ((booking.getStripeCheckoutSessionId() != null && !booking.getStripeCheckoutSessionId().isBlank())
                || (booking.getStripePaymentIntentId() != null && !booking.getStripePaymentIntentId().isBlank())
                || booking.getStripeExpectedAmountMinor() != null
                || booking.getStripeExpectedApplicationFeeMinor() != null
                || booking.getStripeCurrencyCode() != null
                || booking.getStripeConnectedAccountId() != null
                || (booking.getCheckoutUrl() != null && !booking.getCheckoutUrl().isBlank())) {
            throw new ConflictException("Die Buchung enthält bereits Stripe-Checkout-Daten.");
        }
    }

    private void requirePayPalCheckoutMayStart(Booking booking) {
        if (!"UNPAID".equals(booking.getPaymentStatus())) {
            throw new ConflictException("Für diese Buchung wurde bereits eine Zahlung gestartet.");
        }
        if ((booking.getPaypalOrderId() != null && !booking.getPaypalOrderId().isBlank())
                || (booking.getPaypalCaptureId() != null && !booking.getPaypalCaptureId().isBlank())
                || booking.getPaypalExpectedAmount() != null
                || booking.getPaypalCurrencyCode() != null
                || booking.getPaypalPayeeMerchantId() != null
                || (booking.getCheckoutUrl() != null && !booking.getCheckoutUrl().isBlank())) {
            throw new ConflictException("Die Buchung enthält bereits PayPal-Checkout-Daten.");
        }
    }

    private BookingStatus parsePersistedStatus(String status) {
        try {
            return BookingStatus.valueOf(status);
        } catch (Exception e) {
            throw new ConflictException("Statuswechsel ist für den aktuellen Buchungsstatus nicht erlaubt.");
        }
    }

    private boolean isAllowedProviderTransition(BookingStatus source, BookingStatus target) {
        return (source == BookingStatus.PENDING
                && (target == BookingStatus.ACCEPTED || target == BookingStatus.REJECTED))
                || (source == BookingStatus.ACCEPTED && target == BookingStatus.COMPLETED);
    }

    private void requireAcceptedForCheckout(Booking booking) {
        if (!BookingStatus.ACCEPTED.name().equals(booking.getStatus())) {
            throw new ConflictException("Checkout ist nur für angenommene Buchungen möglich.");
        }
    }

    private boolean isProviderPaypalAvailable(User provider) {
        return payPalService.isProviderCheckoutEligible(provider);
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isDeliveryAvailable(Booking booking) {
        return booking.getDeliveryUrl() != null
                && !booking.getDeliveryUrl().isBlank()
                && "PAID".equals(booking.getPaymentStatus())
                && (booking.getDeliveryExpiresAt() == null || booking.getDeliveryExpiresAt().isAfter(OffsetDateTime.now()));
    }

    private String deliveryAccessPath(Booking booking) {
        if (booking.getDeliveryUrl() == null || booking.getDeliveryUrl().isBlank()) {
            return null;
        }
        return "/api/bookings/" + booking.getId() + "/delivery/open";
    }

    private List<TimeEntryResponse> loadTimeEntries(UUID bookingId) {
        return timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(bookingId).stream()
                .map(entry -> new TimeEntryResponse(
                        entry.getId(),
                        entry.getBooking().getId(),
                        entry.getWorkDate(),
                        entry.getHours(),
                        entry.getNote()
                ))
                .toList();
    }
}
