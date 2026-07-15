package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateServiceRequest;
import at.hcw.serviceratebackend.dto.PageResponse;
import at.hcw.serviceratebackend.dto.ReviewResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UpdateServiceRequest;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "CLEANING",
            "PLUMBING",
            "ELECTRICAL",
            "PAINTING",
            "GARDENING",
            "OTHER",
            "REPAIR"
    );

    private final ServiceOfferingRepository serviceRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final LocationValidationService locationValidationService;
    private final PayPalService payPalService;
    private final StripeConnectService stripeConnectService;

    @Value("${app.backend-base-url:http://localhost:8081}")
    private String backendBaseUrl;

    public ServiceOfferingResponse create(CreateServiceRequest request) {
        User provider = userRepository.findById(request.providerId())
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));
        return createForProvider(request, provider);
    }

    public ServiceOfferingResponse createForProviderEmail(CreateServiceRequest request, String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Handwerker nicht gefunden"));
        return createForProvider(request, provider);
    }

    private ServiceOfferingResponse createForProvider(CreateServiceRequest request, User provider) {
        if (!"PROVIDER".equals(provider.getAccountType())) {
            throw new IllegalArgumentException("Nur Anbieter dürfen Services erstellen.");
        }
        if (!provider.isEmailVerified()) {
            throw new IllegalArgumentException("Bitte verifiziere zuerst deine E-Mail-Adresse.");
        }

        String category = normalizeCategory(request.category());
        BigDecimal price = normalizePrice(request.price());

        // PLZ über die externe Zippopotam.us-API in einen Ortsnamen auflösen (400, falls ungültig)
        String location = locationValidationService.resolveCityName(request.zipCode());

        ServiceOffering service = new ServiceOffering();
        service.setId(UUID.randomUUID());
        service.setProvider(provider);
        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(category);
        service.setPrice(price);
        service.setEstimatedHours(request.estimatedHours());
        List<String> imageUrls = normalizeImageUrls(request.imageUrls(), request.imageUrl());
        service.setImageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0));
        service.setImageUrls(serializeImageUrls(imageUrls));
        service.setDeliverableType(normalizeDeliverableType(request.deliverableType()));
        service.setLocation(location);
        service.setStatus("ACTIVE");

        return mapToSummaryResponse(serviceRepository.save(service));
    }

    public List<ServiceOfferingResponse> getAll() {
        return serviceRepository.findAll().stream()
                .filter(service -> "ACTIVE".equals(service.getStatus()))
                .map(this::mapToSummaryResponse)
                .toList();
    }

    public PageResponse<ServiceOfferingResponse> search(
            int page,
            int size,
            String q,
            String category,
            String location,
            BigDecimal maxPrice,
            Double minRating,
            String sort
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 48));
        Pageable pageable = "rating".equals(sort)
                ? PageRequest.of(safePage, safeSize)
                : PageRequest.of(safePage, safeSize, sortFor(sort));
        String cleanQ = blankToEmpty(q);
        String cleanCategory = blankToEmpty(category);
        String cleanLocation = blankToEmpty(location);
        double cleanMinRating = minRating == null ? 0.0 : Math.max(0.0, minRating);
        Page<ServiceOffering> services = "rating".equals(sort)
                ? serviceRepository.searchActiveOrderByRating(cleanQ, cleanCategory, cleanLocation, maxPrice, cleanMinRating, pageable)
                : serviceRepository.searchActive(cleanQ, cleanCategory, cleanLocation, maxPrice, cleanMinRating, pageable);
        Page<ServiceOfferingResponse> results = services.map(this::mapToSummaryResponse);
        return new PageResponse<>(
                results.getContent(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.getNumber(),
                results.getSize()
        );
    }

    public ServiceOfferingResponse getById(UUID id) {
        return serviceRepository.findById(id)
                .filter(service -> "ACTIVE".equals(service.getStatus()))
                .map(this::mapToDetailResponse)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));
    }

    public ServiceOfferingResponse getByIdForAdmin(UUID id) {
        return serviceRepository.findById(id)
                .map(this::mapToDetailResponse)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));
    }

    public List<ServiceOfferingResponse> getActiveSummariesByProviderId(UUID providerId) {
        return serviceRepository.findByProviderId(providerId).stream()
                .filter(service -> "ACTIVE".equals(service.getStatus()))
                .map(this::mapToSummaryResponse)
                .toList();
    }

    // Nur die Services des eingeloggten Providers (anhand der E-Mail aus dem JWT-Subject)
    public List<ServiceOfferingResponse> getMyServices(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
        return serviceRepository.findByProviderId(provider.getId()).stream()
                .map(this::mapToSummaryResponse)
                .toList();
    }

    @Transactional
    public void deleteForProviderEmail(UUID id, String providerEmail) {
        User provider = requireActiveProvider(providerEmail);
        ServiceOffering service = requireOwnedService(id, provider);
        serviceRepository.delete(service);
    }

    @Transactional
    public ServiceOfferingResponse updateServiceForProviderEmail(
            UUID id,
            UpdateServiceRequest request,
            String providerEmail
    ) {
        User provider = requireActiveProvider(providerEmail);
        ServiceOffering service = requireOwnedService(id, provider);
        String category = normalizeCategory(request.category());
        BigDecimal price = normalizePrice(request.price());

        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCategory(category);
        service.setPrice(price);
        service.setEstimatedHours(request.estimatedHours());
        List<String> imageUrls = normalizeImageUrls(request.imageUrls(), request.imageUrl());
        service.setImageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0));
        service.setImageUrls(serializeImageUrls(imageUrls));
        service.setDeliverableType(normalizeDeliverableType(request.deliverableType()));

        return mapToSummaryResponse(serviceRepository.save(service));
    }

    private User requireActiveProvider(String providerEmail) {
        User provider = userRepository.findByEmail(providerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Provider nicht gefunden"));
        if (!"PROVIDER".equals(provider.getAccountType()) || !"ACTIVE".equals(provider.getStatus())) {
            throw new IllegalArgumentException("Diese Aktion ist nur für aktive Anbieter erlaubt.");
        }
        return provider;
    }

    private ServiceOffering requireOwnedService(UUID id, User provider) {
        ServiceOffering service = serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));
        User owner = service.getProvider();
        if (owner == null || !owner.getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Dieser Service gehört nicht zu diesem Anbieter.");
        }
        return service;
    }

    private ServiceOfferingResponse mapToSummaryResponse(ServiceOffering service) {
        return mapToResponse(service, false);
    }

    private ServiceOfferingResponse mapToDetailResponse(ServiceOffering service) {
        return mapToResponse(service, true);
    }

    // Wandelt eine Entity ins Antwort-DTO um. Listen liefern nur Review-Summaries;
    // Detailseiten laden die vollständigen Reviews.
    private ServiceOfferingResponse mapToResponse(ServiceOffering service, boolean includeReviews) {
        // findAverageRatingByServiceId kann null sein, wenn es noch keine Reviews gibt -> sauber auf 0.0 mappen
        Double avg = reviewRepository.findAverageRatingByServiceId(service.getId());
        double averageRating = (avg != null) ? avg : 0.0;
        Long reviewCount = reviewRepository.findReviewCountByServiceId(service.getId());
        var reviews = includeReviews
                ? reviewRepository.findByBookingServiceOfferingId(service.getId()).stream()
                        .map(reviewService::toResponse)
                        .toList()
                : List.<ReviewResponse>of();
        int trustScore = calculateTrustScore(averageRating, reviewCount, service.getStatus());

        return new ServiceOfferingResponse(
                service.getId(),
                service.getProvider().getId(),
                service.getProvider().getFirstName() + " " + service.getProvider().getLastName(),
                includeReviews ? service.getProvider().getProfileImageUrl() : compactProviderAvatarUrl(service.getProvider()),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getPrice(),
                service.getEstimatedHours(),
                includeReviews ? service.getImageUrl() : compactServiceImageUrl(service, primaryImageValue(service)),
                includeReviews ? parseImageUrls(service.getImageUrls(), service.getImageUrl()) : compactImageUrls(service),
                service.getDeliverableType(),
                service.getStatus(),
                service.getLocation(),
                averageRating,
                reviewCount,
                trustScore,
                isProviderPaypalAvailable(service.getProvider()),
                stripeConnectService.isProviderStripeAvailable(service.getProvider()),
                true,
                reviews
        );
    }

    public Optional<ImageResource> getPrimaryImage(UUID id) {
        return serviceRepository.findById(id)
                .flatMap(service -> decodeDataImage(primaryImageValue(service)))
                .map(image -> thumbnail(image, 640));
    }

    private boolean isProviderPaypalAvailable(User provider) {
        return payPalService.isProviderCheckoutEligible(provider);
    }

    private int calculateTrustScore(double averageRating, long reviewCount, String status) {
        double ratingPoints = (averageRating / 5.0) * 70.0;
        double volumePoints = (Math.min(reviewCount, 20) / 20.0) * 20.0;
        double statusPoints = "ACTIVE".equals(status) ? 10.0 : 0.0;
        return (int) Math.round(Math.min(100.0, ratingPoints + volumePoints + statusPoints));
    }

    private Sort sortFor(String sort) {
        if ("priceAsc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "price", "title");
        }
        if ("priceDesc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.ASC, "title"));
        }
        return Sort.by(Sort.Direction.ASC, "title");
    }

    private String normalizeDeliverableType(String deliverableType) {
        String normalized = deliverableType == null || deliverableType.isBlank()
                ? "ON_SITE"
                : deliverableType.trim().toUpperCase();
        if (!normalized.equals("ON_SITE") && !normalized.equals("DIGITAL") && !normalized.equals("HYBRID")) {
            throw new IllegalArgumentException("Ungültige Lieferart.");
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Ungültige Service-Kategorie.");
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Ungültige Service-Kategorie.");
        }
        return normalized;
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Servicepreise müssen größer als 0 sein.");
        }
        BigDecimal normalized;
        try {
            normalized = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Servicepreise dürfen höchstens zwei Nachkommastellen besitzen.");
        }
        if (normalized.precision() > 19) {
            throw new IllegalArgumentException("Der Servicepreis überschreitet den unterstützten Wertebereich.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private List<String> normalizeImageUrls(List<String> imageUrls, String fallbackImageUrl) {
        List<String> values = new ArrayList<>();
        if (imageUrls != null) {
            List<String> cleaned = imageUrls.stream()
                    .map(this::blankToNull)
                    .filter(value -> value != null)
                    .toList();
            if (cleaned.size() > 10) {
                throw new IllegalArgumentException("Maximal 10 Bilder pro Service erlaubt.");
            }
            values.addAll(cleaned);
        }
        String fallback = blankToNull(fallbackImageUrl);
        if (values.isEmpty() && fallback != null) {
            values.add(fallback);
        }
        return values;
    }

    private String serializeImageUrls(List<String> imageUrls) {
        return imageUrls == null || imageUrls.isEmpty() ? null : String.join("\n", imageUrls);
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

    private List<String> compactImageUrls(ServiceOffering service) {
        return parseImageUrls(service.getImageUrls(), service.getImageUrl()).stream()
                .map(value -> compactServiceImageUrl(service, value))
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String primaryImageValue(ServiceOffering service) {
        List<String> images = parseImageUrls(service.getImageUrls(), service.getImageUrl());
        return images.isEmpty() ? null : images.get(0);
    }

    private String compactProviderAvatarUrl(User provider) {
        if (provider == null) {
            return null;
        }
        String mediaUrl = blankToNull(provider.getProfileImageUrl());
        if (mediaUrl == null) {
            return null;
        }
        if (mediaUrl.regionMatches(true, 0, "data:", 0, 5)) {
            return backendBaseUrl + "/api/providers/" + provider.getId() + "/avatar?v=" + cacheVersion(mediaUrl);
        }
        return mediaUrl;
    }

    private String compactServiceImageUrl(ServiceOffering service, String value) {
        String mediaUrl = blankToNull(value);
        if (mediaUrl == null) {
            return null;
        }
        if (mediaUrl.regionMatches(true, 0, "data:", 0, 5)) {
            return backendBaseUrl + "/api/services/" + service.getId() + "/image?v=" + cacheVersion(mediaUrl);
        }
        return mediaUrl;
    }

    private String cacheVersion(String value) {
        return Integer.toHexString(value.hashCode());
    }

    private Optional<ImageResource> decodeDataImage(String value) {
        String dataUrl = blankToNull(value);
        if (dataUrl == null || !dataUrl.regionMatches(true, 0, "data:image/", 0, 11)) {
            return Optional.empty();
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).toLowerCase().contains(";base64")) {
            return Optional.empty();
        }
        String metadata = dataUrl.substring(5, comma).toLowerCase();
        String contentType = metadata.substring(0, metadata.indexOf(';'));
        if (!List.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(contentType)) {
            return Optional.empty();
        }
        return Optional.of(new ImageResource(Base64.getDecoder().decode(dataUrl.substring(comma + 1)), contentType));
    }

    private ImageResource thumbnail(ImageResource image, int maxEdge) {
        if (!List.of("image/jpeg", "image/png").contains(image.contentType())) {
            return image;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            if (source == null) {
                return image;
            }
            int largestEdge = Math.max(source.getWidth(), source.getHeight());
            if (largestEdge <= maxEdge) {
                return image;
            }
            double scale = maxEdge / (double) largestEdge;
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", out);
            return new ImageResource(out.toByteArray(), "image/jpeg");
        } catch (IOException ignored) {
            return image;
        }
    }
}
