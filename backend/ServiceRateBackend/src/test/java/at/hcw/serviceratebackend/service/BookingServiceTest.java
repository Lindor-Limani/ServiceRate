package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.dto.CreateTimeEntryRequest;
import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.dto.PublishDeliveryRequest;
import at.hcw.serviceratebackend.dto.UpdateBookingWorkRequest;
import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
import at.hcw.serviceratebackend.model.common.exception.ConflictException;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.TimeEntryRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceOfferingRepository serviceRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private MailService mailService;
    @Mock
    private PayPalService payPalService;
    @Mock
    private StripeConnectService stripeConnectService;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                userRepository,
                serviceRepository,
                reviewRepository,
                reviewService,
                timeEntryRepository,
                mailService,
                payPalService,
                stripeConnectService
        );
        ReflectionTestUtils.setField(bookingService, "platformFeePercent", 10.0);
        ReflectionTestUtils.setField(bookingService, "platformFeeFixed", 1.0);
        ReflectionTestUtils.setField(bookingService, "backendBaseUrl", "http://localhost:8081");
    }

    @Test
    void createBooking_createsPendingBookingForVerifiedCustomerAndSendsMail() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        ServiceOffering offering = offering(user("provider@example.com", "PROVIDER", true), 120.0);
        offering.setCurrencyCode("eur");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(any())).thenReturn(List.of());

        var response = bookingService.createBooking(
                new CreateBookingRequest(customer.getId(), offering.getId(), LocalDate.of(2026, 8, 1)),
                "customer@example.com"
        );

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getServiceOffering()).isSameAs(offering);
        assertThat(saved.getBookedUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(saved.getBookingCurrencyCode()).isEqualTo("EUR");
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING.name());
        assertThat(saved.getBookingDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING.name());
        verify(mailService).sendBookingCreatedMail(saved);
    }

    @Test
    void createBooking_rejectsInvalidPriceOrCurrencyBeforePersistingSnapshot() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        ServiceOffering offering = offering(user("provider@example.com", "PROVIDER", true), 100.0);
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        CreateBookingRequest request = new CreateBookingRequest(
                customer.getId(), offering.getId(), LocalDate.now().plusDays(1)
        );

        for (Double invalidPrice : new Double[]{null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            offering.setPrice(invalidPrice);
            assertThatThrownBy(() -> bookingService.createBooking(request, customer.getEmail()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Buchungen erfordern einen positiven endlichen Angebotspreis.");
        }

        offering.setPrice(12.345);
        assertThatThrownBy(() -> bookingService.createBooking(request, customer.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Angebotspreise dürfen höchstens zwei Nachkommastellen besitzen.");

        offering.setPrice(100.0);
        for (String invalidCurrency : new String[]{null, "", "EURO", "AAA"}) {
            offering.setCurrencyCode(invalidCurrency);
            assertThatThrownBy(() -> bookingService.createBooking(request, customer.getEmail()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Buchungen erfordern einen gültigen ISO-Währungscode.");
        }

        verify(bookingRepository, never()).save(any());
        verify(mailService, never()).sendBookingCreatedMail(any());
    }

    @Test
    void createBooking_rejectsProviderAccountAndUnverifiedCustomer() {
        User provider = user("provider@example.com", "PROVIDER", true);
        User unverifiedCustomer = user("customer@example.com", "CUSTOMER", false);
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(unverifiedCustomer));

        assertThatThrownBy(() -> bookingService.createBooking(
                new CreateBookingRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now()),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diese Aktion ist für diese Rolle nicht erlaubt.");

        assertThatThrownBy(() -> bookingService.createBooking(
                new CreateBookingRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now()),
                "customer@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bitte verifiziere zuerst deine E-Mail-Adresse.");

        verify(serviceRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_rejectsPastBookingDate() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        ServiceOffering offering = offering(user("provider@example.com", "PROVIDER", true), 120.0);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        assertThatThrownBy(() -> bookingService.createBooking(
                new CreateBookingRequest(customer.getId(), offering.getId(), LocalDate.now().minusDays(1)),
                "customer@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Termine in der Vergangenheit können nicht gebucht werden.");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_usesServiceImageEndpointForUploadedPreviewImage() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        ServiceOffering offering = offering(user("provider@example.com", "PROVIDER", true), 120.0);
        offering.setImageUrl(null);
        offering.setImageUrls("data:image/png;base64,preview-image");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(any())).thenReturn(List.of());

        var response = bookingService.createBooking(
                new CreateBookingRequest(customer.getId(), offering.getId(), LocalDate.of(2026, 8, 1)),
                "customer@example.com"
        );

        assertThat(response.serviceId()).isEqualTo(offering.getId());
        assertThat(response.serviceImageUrl())
                .startsWith("http://localhost:8081/api/services/" + offering.getId() + "/image?v=");
        assertThat(response.serviceHasImage()).isTrue();
    }

    @Test
    void updateBookingStatus_enforcesCompleteProviderTransitionMatrix() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId())).thenReturn(List.of());

        for (BookingStatus source : BookingStatus.values()) {
            for (BookingStatus target : BookingStatus.values()) {
                booking.setStatus(source.name());
                boolean allowed = (source == BookingStatus.PENDING
                        && (target == BookingStatus.ACCEPTED || target == BookingStatus.REJECTED))
                        || (source == BookingStatus.ACCEPTED && target == BookingStatus.COMPLETED);

                if (allowed) {
                    var response = bookingService.updateBookingStatus(
                            booking.getId(), target.name(), "provider@example.com"
                    );
                    assertThat(response.status()).isEqualTo(target.name());
                } else {
                    assertThatThrownBy(() -> bookingService.updateBookingStatus(
                            booking.getId(), target.name(), "provider@example.com"
                    ))
                            .isInstanceOf(ConflictException.class)
                            .hasMessage("Statuswechsel ist für den aktuellen Buchungsstatus nicht erlaubt.");
                    assertThat(booking.getStatus()).isEqualTo(source.name());
                }
            }
        }

        verify(bookingRepository, times(3)).save(booking);
        verify(mailService, times(3)).sendBookingStatusMail(booking);
    }

    @Test
    void updateBookingStatus_rejectsInvalidTargetAndInvalidPersistedSourceWithoutSideEffect() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));

        for (String invalidTarget : new String[]{null, "", "UNKNOWN"}) {
            booking.setStatus(BookingStatus.PENDING.name());
            assertThatThrownBy(() -> bookingService.updateBookingStatus(
                    booking.getId(), invalidTarget, "provider@example.com"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültiger Buchungsstatus.");
        }
        for (String invalidSource : new String[]{null, "", "UNKNOWN"}) {
            booking.setStatus(invalidSource);
            assertThatThrownBy(() -> bookingService.updateBookingStatus(
                    booking.getId(), BookingStatus.ACCEPTED.name(), "provider@example.com"
            ))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Statuswechsel ist für den aktuellen Buchungsstatus nicht erlaubt.");
            assertThat(booking.getStatus()).isEqualTo(invalidSource);
        }

        verify(bookingRepository, never()).save(any());
        verify(mailService, never()).sendBookingStatusMail(any());
    }

    @Test
    void updateBookingStatus_rejectsProviderThatDoesNotOwnBooking() {
        User owner = user("owner@example.com", "PROVIDER", true);
        User otherProvider = user("other@example.com", "PROVIDER", true);
        Booking booking = booking(owner);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherProvider));

        assertThatThrownBy(() -> bookingService.updateBookingStatus(booking.getId(), "ACCEPTED", "other@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diese Buchung gehört nicht zu diesem Anbieter.");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void addTimeEntry_rejectsNonPositiveHours() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> bookingService.addTimeEntry(
                booking.getId(),
                new CreateTimeEntryRequest(LocalDate.now(), 0.0, "invalid"),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bitte positive Stunden angeben.");

        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    void createCheckoutForOfflinePayment_calculatesMarketplaceAmountsAndSetsSettlementStatus() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        booking.setActualHours(2.5);
        booking.setBookedUnitPrice(new BigDecimal("80.00"));
        booking.setBookingCurrencyCode("EUR");
        booking.getServiceOffering().setPrice(999.0);
        booking.getServiceOffering().setCurrencyCode("USD");
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId())).thenReturn(List.of());

        var response = bookingService.createCheckout(
                booking.getId(),
                new CreateCheckoutRequest("bank_transfer", false),
                "customer@example.com"
        );

        assertThat(response.paymentProvider()).isEqualTo("BANK_TRANSFER");
        assertThat(response.paymentStatus()).isEqualTo("AWAITING_OFFLINE_PAYMENT");
        assertThat(response.grossAmount()).isEqualTo(200.0);
        assertThat(response.platformFeeAmount()).isEqualTo(21.0);
        assertThat(response.providerReceivableAmount()).isEqualTo(179.0);
        assertThat(response.servicePrice()).isEqualTo(80.0);
        assertThat(response.settlementStatus()).isEqualTo("NOT_READY");
    }

    @Test
    void createCheckoutRejectsNonFiniteHoursBeforeFinancialMutation() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        booking.setActualHours(Double.NaN);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("BANK_TRANSFER", false), customer.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Checkout erfordert eine endliche Stundenanzahl.");

        assertThat(booking.getGrossAmount()).isNull();
        assertThat(booking.getPlatformFeeAmount()).isNull();
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createStripeCheckoutReturnsCommittedSessionForSequentialReplays() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(stripeConnectService.createCheckoutSession(any(Booking.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    Booking changed = invocation.getArgument(0);
                    changed.setStripeCheckoutSessionId("cs_booking_once");
                    changed.setStripePaymentIntentId("pi_booking_once");
                    changed.setStripeExpectedAmountMinor(10000L);
                    changed.setStripeExpectedApplicationFeeMinor(1100L);
                    changed.setStripeCurrencyCode("EUR");
                    changed.setStripeConnectedAccountId("acct_booking_once");
                    changed.setCheckoutUrl("https://checkout.stripe.test/once");
                    changed.setPaymentProvider("CARD");
                    changed.setPaymentStatus("CHECKOUT_CREATED");
                    changed.setSettlementStatus("STRIPE_DESTINATION_CHARGE_PENDING");
                    return new StripeConnectService.StripeCheckout(
                            changed.getStripeCheckoutSessionId(), changed.getCheckoutUrl()
                    );
                });
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId()))
                .thenReturn(List.of());

        BookingResponse first = bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        );
        BookingResponse replay = bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", true), customer.getEmail()
        );
        booking.setPaymentStatus("FAILED");
        BookingResponse failedRetry = bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        );

        assertThat(first.stripeCheckoutSessionId()).isEqualTo("cs_booking_once");
        assertThat(replay.stripeCheckoutSessionId()).isEqualTo(first.stripeCheckoutSessionId());
        assertThat(replay.checkoutUrl()).isEqualTo(first.checkoutUrl());
        assertThat(failedRetry.stripeCheckoutSessionId()).isEqualTo(first.stripeCheckoutSessionId());
        assertThat(booking.getStripePaymentIntentId()).isEqualTo("pi_booking_once");
        assertThat(booking.getStripeExpectedAmountMinor()).isEqualTo(10000L);
        assertThat(booking.getStripeExpectedApplicationFeeMinor()).isEqualTo(1100L);
        assertThat(booking.getStripeCurrencyCode()).isEqualTo("EUR");
        assertThat(booking.getStripeConnectedAccountId()).isEqualTo("acct_booking_once");
        verify(stripeConnectService, times(1)).createCheckoutSession(booking, false);
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void createStripeCheckoutRejectsOtherActiveOrInconsistentPaymentState() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Für diese Buchung wurde bereits eine Zahlung gestartet.");

        booking.setPaymentProvider("CARD");
        booking.setStripeCheckoutSessionId(null);
        booking.setCheckoutUrl(null);
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Der vorhandene Stripe-Checkout ist unvollständig und kann nicht erneut verwendet werden.");

        booking.setPaymentProvider("MANUAL");
        booking.setPaymentStatus("UNPAID");
        booking.setStripeCheckoutSessionId("cs_stale");
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Die Buchung enthält bereits Stripe-Checkout-Daten.");

        booking.setStripeCheckoutSessionId(null);
        booking.setStripeExpectedApplicationFeeMinor(1100L);
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("CARD", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Die Buchung enthält bereits Stripe-Checkout-Daten.");

        verify(stripeConnectService, never()).createCheckoutSession(any(), anyBoolean());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createCheckoutForPayPal_usesOnlyEligibleBookingProvider() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        User provider = user("provider@example.com", "PROVIDER", true);
        provider.setPaypalMerchantId("verified-merchant");
        Booking booking = booking(provider);
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        booking.getServiceOffering().setPrice(999.0);
        booking.getServiceOffering().setCurrencyCode("USD");
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(payPalService.isProviderCheckoutEligible(provider)).thenReturn(true);
        when(payPalService.createOrder(booking))
                .thenReturn(new PayPalService.PayPalOrder("ORDER-1", "https://paypal.example/approve"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId())).thenReturn(List.of());

        var response = bookingService.createCheckout(
                booking.getId(),
                new CreateCheckoutRequest("paypal", false),
                "customer@example.com"
        );
        var replay = bookingService.createCheckout(
                booking.getId(),
                new CreateCheckoutRequest("PAYPAL", false),
                "customer@example.com"
        );

        assertThat(response.paymentProvider()).isEqualTo("PAYPAL");
        assertThat(response.paymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(response.paypalOrderId()).isEqualTo("ORDER-1");
        assertThat(response.checkoutUrl()).isEqualTo("https://paypal.example/approve");
        assertThat(replay.paypalOrderId()).isEqualTo(response.paypalOrderId());
        assertThat(replay.checkoutUrl()).isEqualTo(response.checkoutUrl());
        assertThat(response.providerPaypalAvailable()).isTrue();
        assertThat(booking.getPaypalExpectedAmount()).isEqualByComparingTo("100.00");
        assertThat(booking.getPaypalCurrencyCode()).isEqualTo("EUR");
        assertThat(booking.getPaypalPayeeMerchantId()).isEqualTo("verified-merchant");
        verify(payPalService, times(1)).createOrder(booking);
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    void createPayPalCheckoutRejectsOtherActiveOrInconsistentPaymentState() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        booking.setPaymentProvider("CARD");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Für diese Buchung wurde bereits eine Zahlung gestartet.");

        booking.setPaymentProvider("PAYPAL");
        booking.setPaypalOrderId(null);
        booking.setCheckoutUrl(null);
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Der vorhandene PayPal-Checkout ist unvollständig und kann nicht erneut verwendet werden.");

        booking.setPaymentProvider("MANUAL");
        booking.setPaymentStatus("UNPAID");
        booking.setPaypalOrderId("ORDER-stale");
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Die Buchung enthält bereits PayPal-Checkout-Daten.");

        verify(payPalService, never()).createOrder(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createCheckoutForPayPal_rejectsIncompleteProviderBeforeCreatingOrder() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        User provider = user("provider@example.com", "PROVIDER", true);
        provider.setPaypalMerchantId("stale-merchant");
        provider.setPaypalOnboardingStatus("ACTION_REQUIRED");
        provider.setPaypalPermissionsGranted(true);
        provider.setPaypalEmailConfirmed(true);
        Booking booking = booking(provider);
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        when(payPalService.isProviderCheckoutEligible(provider)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(),
                new CreateCheckoutRequest("paypal", false),
                "customer@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PayPal-Checkout ist für diesen Anbieter nicht vollständig verifiziert.");

        verify(payPalService, never()).createOrder(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createCheckoutForPayPal_rejectsMissingOrInvalidBookingSnapshotBeforeCreatingOrder() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        User provider = user("provider@example.com", "PROVIDER", true);
        provider.setPaypalMerchantId("verified-merchant");
        Booking booking = booking(provider);
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        booking.setBookedUnitPrice(null);
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Die Buchung enthält keinen vollständigen unveränderlichen Preis- und Währungssnapshot.");

        booking.setBookedUnitPrice(new BigDecimal("100.00"));
        booking.setBookingCurrencyCode("EURO");
        assertThatThrownBy(() -> bookingService.createCheckout(
                booking.getId(), new CreateCheckoutRequest("PAYPAL", false), customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Die Buchung enthält keinen vollständigen unveränderlichen Preis- und Währungssnapshot.");

        assertThat(booking.getPaypalExpectedAmount()).isNull();
        assertThat(booking.getPaypalCurrencyCode()).isNull();
        assertThat(booking.getPaypalPayeeMerchantId()).isNull();
        verify(payPalService, never()).createOrder(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createCheckout_rejectsEveryNonAcceptedStatusBeforeMutationOrExternalCall() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        String[] rejectedStatuses = {
                BookingStatus.PENDING.name(),
                BookingStatus.REJECTED.name(),
                BookingStatus.COMPLETED.name(),
                BookingStatus.CANCELLED.name(),
                null,
                "",
                "UNKNOWN",
                BookingStatus.REJECTED.name()
        };
        for (String rejectedStatus : rejectedStatuses) {
            booking.setStatus(rejectedStatus);

            assertThatThrownBy(() -> bookingService.createCheckout(
                    booking.getId(),
                    new CreateCheckoutRequest("PAYPAL", false),
                    "customer@example.com"
            ))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Checkout ist nur für angenommene Buchungen möglich.");

            assertThat(booking.getPaymentStatus()).isEqualTo("UNPAID");
            assertThat(booking.getPaymentProvider()).isEqualTo("MANUAL");
            assertThat(booking.getGrossAmount()).isNull();
            assertThat(booking.getPlatformFeeAmount()).isNull();
            assertThat(booking.getProviderReceivableAmount()).isNull();
            assertThat(booking.getPaypalOrderId()).isNull();
            assertThat(booking.getCheckoutUrl()).isNull();
        }

        verify(payPalService, never()).createOrder(any());
        verify(stripeConnectService, never()).createCheckoutSession(any(), anyBoolean());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void capturePayPalPayment_isIdempotentAfterSuccessfulCapture() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.ACCEPTED.name());
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-1");
        setPayPalSnapshot(booking);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(payPalService.captureOrder(booking.getId(), "ORDER-1"))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1",
                        booking.getId().toString(), booking.getId().toString(),
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ));
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId())).thenReturn(List.of());

        var first = bookingService.capturePayPalPayment(booking.getId(), "ORDER-1", customer.getEmail());
        var replay = bookingService.capturePayPalPayment(booking.getId(), "ORDER-1", customer.getEmail());

        assertThat(first.paymentStatus()).isEqualTo("PAID");
        assertThat(first.paypalCaptureId()).isEqualTo("CAPTURE-1");
        assertThat(first.paidAt()).isNotNull();
        assertThat(replay.paymentStatus()).isEqualTo("PAID");
        assertThat(replay.paypalCaptureId()).isEqualTo("CAPTURE-1");
        verify(payPalService, times(1)).captureOrder(booking.getId(), "ORDER-1");
        verify(bookingRepository, times(1)).saveAndFlush(booking);
        verify(mailService, times(1)).sendPaymentRecordedMail(booking);
    }

    @Test
    void capturePayPalPayment_rejectsInvalidPaymentSourceBeforeExternalCall() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setPaymentProvider("PAYPAL");
        booking.setPaypalOrderId("ORDER-1");
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        for (String invalidStatus : new String[]{"UNPAID", "AWAITING_OFFLINE_PAYMENT", "REFUNDED", null, "", "UNKNOWN"}) {
            booking.setPaymentStatus(invalidStatus);
            assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                    booking.getId(), "ORDER-1", customer.getEmail()
            ))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("PayPal-Capture ist nur für einen gestarteten Checkout möglich.");
            assertThat(booking.getPaymentStatus()).isEqualTo(invalidStatus);
            assertThat(booking.getPaypalCaptureId()).isNull();
            assertThat(booking.getPaidAt()).isNull();
        }

        verify(payPalService, never()).captureOrder(any(), any());
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendPaymentRecordedMail(any());
    }

    @Test
    void capturePayPalPayment_rejectsIncompleteProviderResponsesWithoutMutation() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-1");
        setPayPalSnapshot(booking);
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(payPalService.captureOrder(booking.getId(), "ORDER-1"))
                .thenReturn(null)
                .thenReturn(new PayPalService.PayPalCapture(
                        "PENDING", "CAPTURE-PENDING", "ORDER-1",
                        booking.getId().toString(), booking.getId().toString(),
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", null, "ORDER-1",
                        booking.getId().toString(), booking.getId().toString(),
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", " ", "ORDER-1",
                        booking.getId().toString(), booking.getId().toString(),
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ));

        assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                booking.getId(), "ORDER-1", customer.getEmail()
        )).hasMessage("PayPal Capture lieferte keine gültige Antwort.");
        assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                booking.getId(), "ORDER-1", customer.getEmail()
        )).hasMessage("PayPal Zahlung wurde nicht abgeschlossen. Status: PENDING");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                    booking.getId(), "ORDER-1", customer.getEmail()
            )).hasMessage("PayPal Capture lieferte keine Capture-ID.");
        }

        assertThat(booking.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(booking.getPaypalCaptureId()).isNull();
        assertThat(booking.getPaidAt()).isNull();
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendPaymentRecordedMail(any());
    }

    @Test
    void capturePayPalPayment_rejectsMissingCheckoutSnapshotBeforeExternalCall() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-1");
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                booking.getId(), "ORDER-1", customer.getEmail()
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Der PayPal-Checkout enthält keine vollständigen unveränderlichen Zahlungs-Sollwerte.");

        verify(payPalService, never()).captureOrder(any(), any());
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendPaymentRecordedMail(any());
    }

    @Test
    void capturePayPalPayment_rejectsMismatchedFinancialValuesWithoutMutation() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-1");
        setPayPalSnapshot(booking);
        String bookingId = booking.getId().toString();
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(payPalService.captureOrder(booking.getId(), "ORDER-1"))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        null, "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("99.99"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), null, "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), " ", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), "USD", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", null
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", " "
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", "other-merchant"
                ));

        String[] expectedErrors = {
                "PayPal Capture enthält nicht den erwarteten Betrag.",
                "PayPal Capture enthält nicht den erwarteten Betrag.",
                "PayPal Capture enthält nicht die erwartete Währung.",
                "PayPal Capture enthält nicht die erwartete Währung.",
                "PayPal Capture enthält nicht die erwartete Währung.",
                "PayPal Capture enthält nicht den erwarteten Zahlungsempfänger.",
                "PayPal Capture enthält nicht den erwarteten Zahlungsempfänger.",
                "PayPal Capture enthält nicht den erwarteten Zahlungsempfänger."
        };
        for (String expectedError : expectedErrors) {
            assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                    booking.getId(), "ORDER-1", customer.getEmail()
            )).hasMessage(expectedError);
        }

        assertThat(booking.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(booking.getPaypalCaptureId()).isNull();
        assertThat(booking.getPaidAt()).isNull();
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendPaymentRecordedMail(any());
    }

    @Test
    void capturePayPalPayment_rejectsMismatchedProviderBindingWithoutMutation() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(user("provider@example.com", "PROVIDER", true));
        booking.setCustomer(customer);
        booking.setPaymentProvider("PAYPAL");
        booking.setPaymentStatus("CHECKOUT_CREATED");
        booking.setPaypalOrderId("ORDER-1");
        setPayPalSnapshot(booking);
        String bookingId = booking.getId().toString();
        when(bookingRepository.findByIdForStateTransition(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(payPalService.captureOrder(booking.getId(), "ORDER-1"))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", null, bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", " ", bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-OTHER", bookingId, bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", null, bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", " ", bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", UUID.randomUUID().toString(), bookingId,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, null,
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, " ",
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ))
                .thenReturn(new PayPalService.PayPalCapture(
                        "COMPLETED", "CAPTURE-1", "ORDER-1", bookingId, UUID.randomUUID().toString(),
                        new BigDecimal("100.00"), "EUR", "verified-merchant"
                ));

        String[] expectedErrors = {
                "PayPal Capture ist nicht der erwarteten Order zugeordnet.",
                "PayPal Capture ist nicht der erwarteten Order zugeordnet.",
                "PayPal Capture ist nicht der erwarteten Order zugeordnet.",
                "PayPal Capture enthält keine passende Buchungsreferenz.",
                "PayPal Capture enthält keine passende Buchungsreferenz.",
                "PayPal Capture enthält keine passende Buchungsreferenz.",
                "PayPal Capture enthält keine passende Buchungskennung.",
                "PayPal Capture enthält keine passende Buchungskennung.",
                "PayPal Capture enthält keine passende Buchungskennung."
        };
        for (String expectedError : expectedErrors) {
            assertThatThrownBy(() -> bookingService.capturePayPalPayment(
                    booking.getId(), "ORDER-1", customer.getEmail()
            )).hasMessage(expectedError);
        }

        assertThat(booking.getPaymentStatus()).isEqualTo("CHECKOUT_CREATED");
        assertThat(booking.getPaypalCaptureId()).isNull();
        assertThat(booking.getPaidAt()).isNull();
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(mailService, never()).sendPaymentRecordedMail(any());
    }

    @Test
    void resolveDeliveryUrl_allowsProviderBeforePaymentButCustomerOnlyAfterPayment() {
        User provider = user("provider@example.com", "PROVIDER", true);
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = booking(provider);
        booking.setCustomer(customer);
        booking.setDeliveryUrl("https://files.example.com/result.pdf");
        booking.setDeliveryExpiresAt(OffsetDateTime.now().plusHours(1));
        booking.setPaymentStatus("UNPAID");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        assertThat(bookingService.resolveDeliveryUrl(booking.getId(), "provider@example.com"))
                .isEqualTo("https://files.example.com/result.pdf");

        assertThatThrownBy(() -> bookingService.resolveDeliveryUrl(booking.getId(), "customer@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Die Lieferung ist erst nach Zahlung verfügbar.");

        booking.setPaymentStatus("PAID");
        assertThat(bookingService.resolveDeliveryUrl(booking.getId(), "customer@example.com"))
                .isEqualTo("https://files.example.com/result.pdf");
    }

    @Test
    void updateWorkLog_rejectsNegativeActualHours() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> bookingService.updateWorkLog(
                booking.getId(),
                new UpdateBookingWorkRequest(-0.5, "bad"),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stunden dürfen nicht negativ sein.");
    }

    @Test
    void publishDelivery_rejectsBlankDeliveryUrl() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> bookingService.publishDelivery(
                booking.getId(),
                new PublishDeliveryRequest(" ", "Resultat", 24),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bitte einen Liefer-Link angeben.");
    }

    private void setPayPalSnapshot(Booking booking) {
        booking.setPaypalExpectedAmount(new BigDecimal("100.00"));
        booking.setPaypalCurrencyCode("EUR");
        booking.setPaypalPayeeMerchantId("verified-merchant");
    }

    private Booking booking(User provider) {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering(provider, 100.0));
        booking.setBookedUnitPrice(new BigDecimal("100.00"));
        booking.setBookingCurrencyCode("EUR");
        booking.setServiceDate(OffsetDateTime.now().plusDays(3));
        booking.setBookingDate(LocalDate.now().plusDays(5));
        booking.setStatus(BookingStatus.PENDING.name());
        booking.setPaymentStatus("UNPAID");
        return booking;
    }

    private ServiceOffering offering(User provider, Double price) {
        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Service");
        offering.setCategory("REPAIR");
        offering.setPrice(price);
        offering.setStatus("ACTIVE");
        offering.setDeliverableType("ON_SITE");
        return offering;
    }

    private User user(String email, String accountType, boolean emailVerified) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName(accountType);
        user.setLastName("User");
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        user.setEmailVerified(emailVerified);
        return user;
    }
}
