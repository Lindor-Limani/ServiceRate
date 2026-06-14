package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UpdateUserRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.common.enums.AccountType;
import at.hcw.serviceratebackend.model.common.enums.UserStatus;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ServiceOfferingRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder; // <-- Das ist neu!

    // --- CREATE ---
    public UserResponse create(CreateUserRequest request) {
        // basic validations
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("E-Mail existiert bereits");
        }
       // String normalizedType = normalizeAndValidateAccountType(request.accountType());

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setAccountType(request.accountType());
        user.setStatus(UserStatus.ACTIVE.name());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    // --- READ ALL ---
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    // --- READ ONE ---
    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
        return toResponse(user);
    }

    // --- UPDATE ---
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));

        if (request.email() != null && !request.email().isBlank()) {
            Optional<User> existing = userRepository.findByEmail(request.email());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new IllegalArgumentException("E-Mail existiert bereits");
            }
            user.setEmail(request.email());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.accountType() != null && !request.accountType().isBlank()) {
            user.setAccountType(request.accountType());
        }
        if (request.status() != null && !request.status().isBlank()) {
            // validate against enum values if possible
            try {
                UserStatus.valueOf(request.status().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Ungültiger Benutzerstatus");
            }
            user.setStatus(request.status().toUpperCase(Locale.ROOT));
        }

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    // --- DELETE ---
    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            // idempotent style delete -> treat as not found error message for client clarity
            throw new IllegalArgumentException("User nicht gefunden");
        }
        // 1) Delete bookings where the user is a customer
        List<Booking> customerBookings = bookingRepository.findByCustomer_Id(id);
        bookingRepository.deleteAll(customerBookings);

        // 2) Delete bookings where the user is provider via their offerings
        List<Booking> providerBookings = bookingRepository.findByServiceOffering_Provider_Id(id);
        bookingRepository.deleteAll(providerBookings);

        // 3) Delete the user's service offerings
        List<ServiceOffering> offerings = serviceOfferingRepository.findByProviderId(id);
        serviceOfferingRepository.deleteAll(offerings);

        // 4) Finally delete the user
        userRepository.deleteById(id);
    }

    // --- helpers ---
    /* private String normalizeAndValidateAccountType(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("accountType ist erforderlich");
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        boolean valid = false;
        for (AccountType t : AccountType.values()) {
            if (t.name().equals(normalized)) { valid = true; break; }
        }
        if (!valid) {
            throw new IllegalArgumentException("Ungültiger accountType. Erlaubt: " + java.util.Arrays.toString(AccountType.values()));
        }
        return normalized;
    }*/

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAccountType(),
                user.getStatus()
        );
    }
}