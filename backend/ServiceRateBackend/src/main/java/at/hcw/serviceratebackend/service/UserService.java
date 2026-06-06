package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse create(CreateUserRequest request) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());

        // TODO: Später mit passwordEncoder.encode() hashen!
        user.setPasswordHash(request.password());

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        // Einfacher String statt komplexes Enum
        user.setAccountType(request.accountType().toUpperCase());
        user.setStatus("ACTIVE");

        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getAccountType(),
                saved.getStatus()
        );
    }
}