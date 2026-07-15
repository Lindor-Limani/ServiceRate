package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.dto.UpdateServiceRequest;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
class ServiceOfferingServiceTest {

    @Mock
    private ServiceOfferingRepository serviceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private LocationValidationService locationValidationService;
    @Mock
    private PayPalService payPalService;
    @Mock
    private StripeConnectService stripeConnectService;

    private ServiceOfferingService service;

    @BeforeEach
    void setUp() {
        service = new ServiceOfferingService(
                serviceRepository,
                userRepository,
                reviewRepository,
                reviewService,
                locationValidationService,
                payPalService,
                stripeConnectService
        );
        ReflectionTestUtils.setField(service, "backendBaseUrl", "http://localhost:8081");
    }

    @Test
    void createForProviderEmail_createsActiveServiceWithResolvedLocationAndNormalizedImages() {
        User provider = provider(true);
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(locationValidationService.resolveCityName("1010")).thenReturn("Wien, Innere Stadt");
        when(serviceRepository.save(any(ServiceOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findAverageRatingByServiceId(any())).thenReturn(null);
        when(reviewRepository.findReviewCountByServiceId(any())).thenReturn(0L);
        when(payPalService.isProviderCheckoutEligible(provider)).thenReturn(true);
        when(stripeConnectService.isProviderStripeAvailable(provider)).thenReturn(false);

        var response = service.createForProviderEmail(new CreateServiceRequest(
                null,
                "Bad sanieren",
                "Komplettservice",
                " plumbing ",
                new BigDecimal("80.00"),
                2.5,
                " https://fallback.example/image.jpg ",
                List.of(" https://example.com/one.jpg ", " ", "https://example.com/two.jpg"),
                " digital ",
                "1010"
        ), "provider@example.com");

        ArgumentCaptor<ServiceOffering> captor = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(serviceRepository).save(captor.capture());
        ServiceOffering saved = captor.getValue();

        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getCategory()).isEqualTo("PLUMBING");
        assertThat(saved.getPrice()).isEqualByComparingTo("80.00");
        assertThat(saved.getLocation()).isEqualTo("Wien, Innere Stadt");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDeliverableType()).isEqualTo("DIGITAL");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/one.jpg");
        assertThat(saved.getImageUrls()).isEqualTo("https://example.com/one.jpg\nhttps://example.com/two.jpg");
        assertThat(response.location()).isEqualTo("Wien, Innere Stadt");
        assertThat(response.imageUrls()).containsExactly("https://example.com/one.jpg", "https://example.com/two.jpg");
        assertThat(response.trustScore()).isEqualTo(10);
        assertThat(response.providerPaypalAvailable()).isTrue();
        assertThat(response.reviews()).isEmpty();
        verify(reviewRepository, never()).findByBookingServiceOfferingId(any());
    }

    @Test
    void getById_includesFullReviewsForDetailPage() {
        User provider = provider(true);
        ServiceOffering offering = offering(provider);
        offering.setImageUrl("data:image/png;base64,full-service-image");
        offering.setImageUrls("data:image/png;base64,full-service-image");
        Review review = new Review();
        UUID reviewId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(reviewRepository.findAverageRatingByServiceId(offering.getId())).thenReturn(5.0);
        when(reviewRepository.findReviewCountByServiceId(offering.getId())).thenReturn(1L);
        when(reviewRepository.findByBookingServiceOfferingId(offering.getId())).thenReturn(List.of(review));
        when(reviewService.toResponse(review)).thenReturn(new ReviewResponse(
                reviewId,
                bookingId,
                "Grace Customer",
                offering.getTitle(),
                5,
                "Top"
        ));
        when(stripeConnectService.isProviderStripeAvailable(provider)).thenReturn(false);

        var response = service.getById(offering.getId());

        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().getFirst().comment()).isEqualTo("Top");
        assertThat(response.imageUrl()).isEqualTo("data:image/png;base64,full-service-image");
        verify(reviewRepository).findByBookingServiceOfferingId(offering.getId());
    }

    @Test
    void getMyServices_usesCompactImageEndpointsForUploadedMedia() {
        User provider = provider(true);
        provider.setProfileImageUrl("data:image/png;base64,avatar");
        ServiceOffering offering = offering(provider);
        offering.setImageUrl(null);
        offering.setImageUrls("data:image/png;base64,preview-image");
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider));
        when(serviceRepository.findByProviderId(provider.getId())).thenReturn(List.of(offering));
        when(reviewRepository.findAverageRatingByServiceId(offering.getId())).thenReturn(0.0);
        when(reviewRepository.findReviewCountByServiceId(offering.getId())).thenReturn(0L);
        when(stripeConnectService.isProviderStripeAvailable(provider)).thenReturn(false);

        var responses = service.getMyServices("provider@example.com");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().imageUrl())
                .startsWith("http://localhost:8081/api/services/" + offering.getId() + "/image?v=");
        assertThat(responses.getFirst().imageUrls())
                .hasSize(1);
        assertThat(responses.getFirst().imageUrls().getFirst())
                .startsWith("http://localhost:8081/api/services/" + offering.getId() + "/image?v=");
        assertThat(responses.getFirst().providerProfileImageUrl())
                .startsWith("http://localhost:8081/api/providers/" + provider.getId() + "/avatar?v=");
        assertThat(responses.getFirst().reviews()).isEmpty();
    }

    @Test
    void createForProviderEmail_rejectsCustomerAccountsBeforeExternalZipLookup() {
        User customer = provider(true);
        customer.setAccountType("CUSTOMER");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.createForProviderEmail(validRequest(), "customer@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nur Anbieter dürfen Services erstellen.");

        verify(locationValidationService, never()).resolveCityName(any());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createForProviderEmail_rejectsUnverifiedProviderBeforeExternalZipLookup() {
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider(false)));

        assertThatThrownBy(() -> service.createForProviderEmail(validRequest(), "provider@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bitte verifiziere zuerst deine E-Mail-Adresse.");

        verify(locationValidationService, never()).resolveCityName(any());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createForProviderEmail_rejectsInvalidCategoriesBeforeExternalZipLookup() {
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider(true)));

        for (String category : new String[]{null, "", "   ", "UNKNOWN", "<img src=x onerror=alert(1)>"}) {
            CreateServiceRequest request = new CreateServiceRequest(
                    null,
                    "Service",
                    "Beschreibung",
                    category,
                    new BigDecimal("50.00"),
                    1.0,
                    null,
                    List.of(),
                    "ON_SITE",
                    "1010"
            );

            assertThatThrownBy(() -> service.createForProviderEmail(request, "provider@example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ungültige Service-Kategorie.");
        }

        verify(locationValidationService, never()).resolveCityName(any());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createForProviderEmail_rejectsInvalidMoneyValuesBeforeExternalZipLookup() {
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider(true)));

        for (BigDecimal invalidPrice : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-0.01")}) {
            assertThatThrownBy(() -> service.createForProviderEmail(
                    createRequestWithPrice(invalidPrice),
                    "provider@example.com"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Servicepreise müssen größer als 0 sein.");
        }

        assertThatThrownBy(() -> service.createForProviderEmail(
                createRequestWithPrice(new BigDecimal("12.345")),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Servicepreise dürfen höchstens zwei Nachkommastellen besitzen.");

        assertThatThrownBy(() -> service.createForProviderEmail(
                createRequestWithPrice(new BigDecimal("100000000000000000.00")),
                "provider@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Servicepreis überschreitet den unterstützten Wertebereich.");

        verify(locationValidationService, never()).resolveCityName(any());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createForProviderEmail_rejectsInvalidDeliverableType() {
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider(true)));
        when(locationValidationService.resolveCityName("1010")).thenReturn("Wien");

        assertThatThrownBy(() -> service.createForProviderEmail(new CreateServiceRequest(
                null,
                "Service",
                "Beschreibung",
                "REPAIR",
                new BigDecimal("50.00"),
                1.0,
                null,
                List.of(),
                "TELEPORT",
                "1010"
        ), "provider@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültige Lieferart.");

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createForProviderEmail_rejectsMoreThanTenImages() {
        when(userRepository.findByEmail("provider@example.com")).thenReturn(Optional.of(provider(true)));
        when(locationValidationService.resolveCityName("1010")).thenReturn("Wien");

        assertThatThrownBy(() -> service.createForProviderEmail(new CreateServiceRequest(
                null,
                "Service",
                "Beschreibung",
                "REPAIR",
                new BigDecimal("50.00"),
                1.0,
                null,
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                "ON_SITE",
                "1010"
        ), "provider@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximal 10 Bilder pro Service erlaubt.");

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateServiceForProviderEmail_updatesOwnedService() {
        User provider = provider(true);
        ServiceOffering offering = offering(provider);
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceRepository.save(offering)).thenReturn(offering);
        when(reviewRepository.findAverageRatingByServiceId(offering.getId())).thenReturn(null);
        when(reviewRepository.findReviewCountByServiceId(offering.getId())).thenReturn(0L);
        when(stripeConnectService.isProviderStripeAvailable(provider)).thenReturn(false);

        var response = service.updateServiceForProviderEmail(
                offering.getId(),
                updateRequest("Neuer Titel"),
                provider.getEmail()
        );

        assertThat(response.title()).isEqualTo("Neuer Titel");
        assertThat(offering.getTitle()).isEqualTo("Neuer Titel");
        verify(serviceRepository).save(offering);
    }

    @Test
    void updateServiceForProviderEmail_rejectsForeignServiceWithoutSaving() {
        User owner = provider(true);
        User otherProvider = provider(true);
        otherProvider.setId(UUID.randomUUID());
        otherProvider.setEmail("other@example.com");
        ServiceOffering offering = offering(owner);
        when(userRepository.findByEmail(otherProvider.getEmail())).thenReturn(Optional.of(otherProvider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        assertThatThrownBy(() -> service.updateServiceForProviderEmail(
                offering.getId(),
                updateRequest("Manipulierter Titel"),
                otherProvider.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieser Service gehört nicht zu diesem Anbieter.");

        assertThat(offering.getTitle()).isEqualTo("Bad sanieren");
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateServiceForProviderEmail_rejectsXssCategoryWithoutMutatingService() {
        User provider = provider(true);
        ServiceOffering offering = offering(provider);
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        assertThatThrownBy(() -> service.updateServiceForProviderEmail(
                offering.getId(),
                updateRequest("Manipulierter Titel", "<svg onload=alert(1)></svg>"),
                provider.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültige Service-Kategorie.");

        assertThat(offering.getTitle()).isEqualTo("Bad sanieren");
        assertThat(offering.getCategory()).isEqualTo("REPAIR");
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateServiceForProviderEmail_rejectsInvalidPriceWithoutMutatingService() {
        User provider = provider(true);
        ServiceOffering offering = offering(provider);
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        UpdateServiceRequest request = new UpdateServiceRequest(
                "Manipulierter Titel",
                "Aktualisierte Beschreibung",
                "REPAIR",
                new BigDecimal("90.001"),
                3.0,
                null,
                List.of(),
                "ON_SITE"
        );

        assertThatThrownBy(() -> service.updateServiceForProviderEmail(
                offering.getId(),
                request,
                provider.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Servicepreise dürfen höchstens zwei Nachkommastellen besitzen.");

        assertThat(offering.getTitle()).isEqualTo("Bad sanieren");
        assertThat(offering.getPrice()).isEqualByComparingTo("80.00");
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void deleteForProviderEmail_deletesOwnedService() {
        User provider = provider(true);
        ServiceOffering offering = offering(provider);
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        service.deleteForProviderEmail(offering.getId(), provider.getEmail());

        verify(serviceRepository).delete(offering);
    }

    @Test
    void deleteForProviderEmail_rejectsForeignServiceWithoutDeleting() {
        User owner = provider(true);
        User otherProvider = provider(true);
        otherProvider.setId(UUID.randomUUID());
        otherProvider.setEmail("other@example.com");
        ServiceOffering offering = offering(owner);
        when(userRepository.findByEmail(otherProvider.getEmail())).thenReturn(Optional.of(otherProvider));
        when(serviceRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        assertThatThrownBy(() -> service.deleteForProviderEmail(offering.getId(), otherProvider.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieser Service gehört nicht zu diesem Anbieter.");

        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void deleteForProviderEmail_rejectsMissingServiceWithoutDeleting() {
        User provider = provider(true);
        UUID missingId = UUID.randomUUID();
        when(userRepository.findByEmail(provider.getEmail())).thenReturn(Optional.of(provider));
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteForProviderEmail(missingId, provider.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service nicht gefunden");

        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void updateServiceForProviderEmail_rejectsInactiveProviderBeforeLoadingService() {
        User inactiveProvider = provider(true);
        inactiveProvider.setStatus("BLOCKED");
        when(userRepository.findByEmail(inactiveProvider.getEmail())).thenReturn(Optional.of(inactiveProvider));

        assertThatThrownBy(() -> service.updateServiceForProviderEmail(
                UUID.randomUUID(),
                updateRequest("Aktualisiert"),
                inactiveProvider.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diese Aktion ist nur für aktive Anbieter erlaubt.");

        verify(serviceRepository, never()).findById(any());
        verify(serviceRepository, never()).save(any());
    }

    private CreateServiceRequest validRequest() {
        return createRequestWithPrice(new BigDecimal("50.00"));
    }

    private CreateServiceRequest createRequestWithPrice(BigDecimal price) {
        return new CreateServiceRequest(
                null,
                "Service",
                "Beschreibung",
                "REPAIR",
                price,
                1.0,
                null,
                List.of("https://example.com/image.jpg"),
                "ON_SITE",
                "1010"
        );
    }

    private UpdateServiceRequest updateRequest(String title) {
        return updateRequest(title, "REPAIR");
    }

    private UpdateServiceRequest updateRequest(String title, String category) {
        return new UpdateServiceRequest(
                title,
                "Aktualisierte Beschreibung",
                category,
                new BigDecimal("90.00"),
                3.0,
                null,
                List.of("https://example.com/updated.jpg"),
                "ON_SITE"
        );
    }

    private User provider(boolean emailVerified) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("provider@example.com");
        user.setPasswordHash("hash");
        user.setFirstName("Pro");
        user.setLastName("Vider");
        user.setAccountType("PROVIDER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(emailVerified);
        return user;
    }

    private ServiceOffering offering(User provider) {
        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Bad sanieren");
        offering.setDescription("Komplettservice");
        offering.setCategory("REPAIR");
        offering.setPrice(new BigDecimal("80.00"));
        offering.setEstimatedHours(2.5);
        offering.setDeliverableType("ON_SITE");
        offering.setStatus("ACTIVE");
        offering.setLocation("Wien");
        return offering;
    }
}
