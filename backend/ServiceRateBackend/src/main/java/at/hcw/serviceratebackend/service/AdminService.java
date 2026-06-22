package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.AdminStatsResponse;
import at.hcw.serviceratebackend.dto.AdminBookingResponse;
import at.hcw.serviceratebackend.dto.AdminReviewResponse;
import at.hcw.serviceratebackend.dto.ReportResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Report;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ReportRepository;
import at.hcw.serviceratebackend.repository.ReviewRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;
    private final ServiceOfferingService serviceOfferingService;

    public AdminStatsResponse stats() {
        List<User> users = userRepository.findAll();
        long providers = users.stream().filter(u -> "PROVIDER".equals(u.getAccountType())).count();
        long customers = users.stream().filter(u -> "CUSTOMER".equals(u.getAccountType())).count();
        long admins = users.stream().filter(u -> "ADMIN".equals(u.getAccountType())).count();
        List<Booking> bookings = bookingRepository.findAll();
        List<Review> reviews = reviewRepository.findAll();
        double averageRating = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        long openBookings = bookings.stream().filter(b -> "PENDING".equals(b.getStatus()) || "ACCEPTED".equals(b.getStatus())).count();
        long completedBookings = bookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count();
        long cancelledBookings = bookings.stream().filter(b -> "REJECTED".equals(b.getStatus()) || "CANCELLED".equals(b.getStatus())).count();
        double paidRevenue = bookings.stream()
                .filter(b -> "PAID".equals(b.getPaymentStatus()))
                .mapToDouble(b -> (b.getActualHours() == null ? 1.0 : b.getActualHours()) * (b.getServiceOffering() == null ? 0.0 : b.getServiceOffering().getPrice()))
                .sum();

        return new AdminStatsResponse(
                users.size(),
                providers,
                customers,
                admins,
                serviceOfferingRepository.count(),
                bookings.size(),
                reviews.size(),
                averageRating,
                openBookings,
                completedBookings,
                cancelledBookings,
                reportRepository.countByStatus("OPEN"),
                paidRevenue
        );
    }

    public List<UserResponse> users() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getProfileImageUrl(), u.getPayoutIban(), u.getAccountType(), u.getStatus()))
                .toList();
    }

    public List<ServiceOfferingResponse> services() {
        return serviceOfferingRepository.findAll().stream()
                .map(s -> serviceOfferingService.getByIdForAdmin(s.getId()))
                .toList();
    }

    public List<AdminBookingResponse> bookings() {
        return bookingRepository.findAll().stream()
                .map(this::toBookingResponse)
                .toList();
    }

    public List<AdminReviewResponse> reviews() {
        return reviewRepository.findAll().stream()
                .map(this::toReviewResponse)
                .toList();
    }

    public List<ReportResponse> reports() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toReportResponse)
                .toList();
    }

    public UserResponse setUserActive(java.util.UUID id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
        user.setStatus(active ? "ACTIVE" : "INACTIVE");
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName(), saved.getProfileImageUrl(), saved.getPayoutIban(), saved.getAccountType(), saved.getStatus());
    }

    public ServiceOfferingResponse setServiceStatus(java.util.UUID id, String status) {
        ServiceOffering service = serviceOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service nicht gefunden"));
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("ACTIVE") && !normalized.equals("HIDDEN") && !normalized.equals("UNDER_REVIEW")) {
            throw new IllegalArgumentException("Ungültiger Service-Status.");
        }
        service.setStatus(normalized);
        serviceOfferingRepository.save(service);
        return serviceOfferingService.getByIdForAdmin(id);
    }

    public ReportResponse setReportStatus(java.util.UUID id, String status) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report nicht gefunden"));
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("OPEN") && !normalized.equals("IN_REVIEW") && !normalized.equals("RESOLVED") && !normalized.equals("REJECTED")) {
            throw new IllegalArgumentException("Ungültiger Report-Status.");
        }
        report.setStatus(normalized);
        return toReportResponse(reportRepository.save(report));
    }

    private AdminBookingResponse toBookingResponse(Booking booking) {
        return new AdminBookingResponse(
                booking.getId(),
                fullName(booking.getCustomer()),
                booking.getServiceOffering() == null ? "Unbekannter Anbieter" : fullName(booking.getServiceOffering().getProvider()),
                booking.getServiceOffering() == null ? "Unbekannter Service" : booking.getServiceOffering().getTitle(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getBookingDate(),
                booking.getPaidAt()
        );
    }

    private AdminReviewResponse toReviewResponse(Review review) {
        Booking booking = review.getBooking();
        return new AdminReviewResponse(
                review.getId(),
                booking != null ? booking.getId() : null,
                booking != null ? fullName(booking.getCustomer()) : "Unbekannt",
                booking != null && booking.getServiceOffering() != null ? booking.getServiceOffering().getTitle() : "Unbekannter Service",
                review.getRating(),
                review.getComment()
        );
    }

    private ReportResponse toReportResponse(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporter().getEmail(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    private String fullName(User user) {
        if (user == null) return "Unbekannt";
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
