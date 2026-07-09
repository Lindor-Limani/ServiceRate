package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateBookingRequest;
import at.hcw.serviceratebackend.dto.CreateCheckoutRequest;
import at.hcw.serviceratebackend.dto.CreateTimeEntryRequest;
import at.hcw.serviceratebackend.dto.PublishDeliveryRequest;
import at.hcw.serviceratebackend.dto.UpdateBookingWorkRequest;
import at.hcw.serviceratebackend.model.common.enums.BookingStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    }

    @Test
    void createBooking_createsPendingBookingForVerifiedCustomerAndSendsMail() {
        User customer = user("customer@example.com", "CUSTOMER", true);
        ServiceOffering offering = offering(user("provider@example.com", "PROVIDER", true), 120.0);
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
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING.name());
        assertThat(saved.getBookingDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING.name());
        verify(mailService).sendBookingCreatedMail(saved);
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
    void updateBookingStatus_allowsOnlyOwningProviderAndAllowedStatuses() {
        User provider = user("provider@example.com", "PROVIDER", true);
        Booking booking = booking(provider);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(List.of());
        when(timeEntryRepository.findByBookingIdOrderByWorkDateDescCreatedAtDesc(booking.getId())).thenReturn(List.of());

        var response = bookingService.updateBookingStatus(booking.getId(), "ACCEPTED", "provider@example.com");

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(mailService).sendBookingStatusMail(booking);

        assertThatThrownBy(() -> bookingService.updateBookingStatus(booking.getId(), "PENDING", "provider@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültiger Buchungsstatus.");
    }

    @Test
    void updateBookingStatus_rejectsProviderThatDoesNotOwnBooking() {
        User owner = user("owner@example.com", "PROVIDER", true);
        User otherProvider = user("other@example.com", "PROVIDER", true);
        Booking booking = booking(owner);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
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
        booking.setCustomer(customer);
        booking.setActualHours(2.5);
        booking.getServiceOffering().setPrice(80.0);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
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
        assertThat(response.settlementStatus()).isEqualTo("NOT_READY");
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

    private Booking booking(User provider) {
        User customer = user("customer@example.com", "CUSTOMER", true);
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering(provider, 100.0));
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
