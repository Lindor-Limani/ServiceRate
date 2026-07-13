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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(service);
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setBookingDate(request.bookingDate()); // vom Kunden gewählter Wunschtermin
        booking.setStatus(BookingStatus.PENDING.name());

        Booking saved = bookingRepository.save(booking);
        mailService.sendBookingCreatedMail(saved);

        return toResponse(saved, null, null);
    }

    // Holt alle Buchungen für einen bestimmten Handwerker
    public java.util.List<BookingResponse> getBookingsForProvider(UUID providerId) {
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new RuntimeException("Provider nicht gefunden"));
        requireAccountType(provider, "PROVIDER");
        requireProviderOwnsBooking(provider, booking);

        BookingStatus targetStatus = parseStatus(newStatus);
        if (targetStatus != BookingStatus.ACCEPTED
                && targetStatus != BookingStatus.REJECTED
                && targetStatus != BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Ungültiger Buchungsstatus.");
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Kunden.");
        }

        String provider = request.provider() == null || request.provider().isBlank()
                ? "MANUAL"
                : request.provider().trim().toUpperCase();
        applyMarketplaceAmounts(booking);
        booking.setPaymentProvider(provider);
        if ("PAYPAL".equals(provider)) {
            requireProviderPayPalAccount(booking);
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
        Booking booking = bookingRepository.findById(bookingId)
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

        PayPalService.PayPalCapture capture = payPalService.captureOrder(orderId);
        if (!"COMPLETED".equalsIgnoreCase(capture.status())) {
            throw new IllegalStateException("PayPal Zahlung wurde nicht abgeschlossen. Status: " + capture.status());
        }

        booking.setPaymentStatus("PAID");
        booking.setPaymentNote("PayPal-Zahlung erfolgreich abgeschlossen.");
        booking.setPaypalCaptureId(capture.captureId());
        booking.setSettlementStatus("PAYPAL_SPLIT_COMPLETED");
        booking.setSettlementNote("PayPal hat die Zahlung beim Provider gecaptured; Plattformgebühr wurde im PayPal-Order-Request angegeben.");
        booking.setPaidAt(OffsetDateTime.now());
        Booking saved = bookingRepository.save(booking);
        mailService.sendPaymentRecordedMail(saved);
        return toResponse(saved, null, findReviewResponse(saved));
    }

    public BookingResponse markPaid(UUID bookingId, String customerEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new RuntimeException("Kunde nicht gefunden"));
        requireAccountType(customer, "CUSTOMER");
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Diese Buchung gehört nicht zu diesem Kunden.");
        }
        booking.setPaymentStatus("PAID");
        booking.setPaymentNote("Online-Zahlung durch Kunden bestätigt.");
        applyMarketplaceAmounts(booking);
        booking.setSettlementStatus("PLATFORM_COLLECTED_PENDING_PROVIDER_PAYOUT");
        booking.setSettlementNote("Plattform hat die Zahlung erfasst und muss den Provider-Netto-Betrag auszahlen.");
        booking.setPaidAt(OffsetDateTime.now());
        Booking saved = bookingRepository.save(booking);
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

    // Holt alle Buchungen für das Kunden-Dashboard
    public java.util.List<BookingResponse> getBookingsForCustomer(UUID customerId) {
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
                servicePrice(booking.getServiceOffering()),
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

    private Double servicePrice(ServiceOffering offering) {
        return offering == null ? 0.0 : offering.getPrice();
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
        double price = booking.getServiceOffering() == null || booking.getServiceOffering().getPrice() == null
                ? 0.0
                : booking.getServiceOffering().getPrice();
        double hours = booking.getActualHours() != null && booking.getActualHours() > 0
                ? booking.getActualHours()
                : 1.0;
        return roundMoney(price * hours);
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
        if (!isProviderPaypalAvailable(provider)) {
            throw new IllegalArgumentException("Dieser Anbieter hat noch kein PayPal-Konto fuer Marketplace-Zahlungen hinterlegt.");
        }
    }

    private boolean isProviderPaypalAvailable(User provider) {
        if (provider == null) {
            return false;
        }
        boolean hasReceiver = (provider.getPaypalMerchantId() != null && !provider.getPaypalMerchantId().isBlank())
                || (provider.getPaypalEmail() != null && !provider.getPaypalEmail().isBlank());
        boolean connected = "CONNECTED".equals(provider.getPaypalOnboardingStatus())
                || "ACTION_REQUIRED".equals(provider.getPaypalOnboardingStatus());
        return hasReceiver && connected;
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
