package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.AdminStatsResponse;
import at.hcw.serviceratebackend.dto.ServiceOfferingResponse;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
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
    private final ServiceOfferingService serviceOfferingService;

    public AdminStatsResponse stats() {
        List<User> users = userRepository.findAll();
        long providers = users.stream().filter(u -> "PROVIDER".equals(u.getAccountType())).count();
        long customers = users.stream().filter(u -> "CUSTOMER".equals(u.getAccountType())).count();

        return new AdminStatsResponse(
                users.size(),
                providers,
                customers,
                serviceOfferingRepository.count(),
                bookingRepository.count(),
                reviewRepository.count()
        );
    }

    public List<UserResponse> users() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getAccountType(), u.getStatus()))
                .toList();
    }

    public List<ServiceOfferingResponse> services() {
        return serviceOfferingRepository.findAll().stream()
                .map(s -> serviceOfferingService.getById(s.getId()))
                .toList();
    }
}
