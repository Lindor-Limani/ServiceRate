package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.common.enums.AccountType;
import at.hcw.serviceratebackend.model.common.enums.UserStatus;
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
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDisplayName(request.firstName() + " " + request.lastName());
        user.setStatus(UserStatus.ACTIVE);
        user.setAccountType(AccountType.valueOf(request.accountType().toUpperCase()));
        user.setLocale("de-AT");
        user.setTimezone("Europe/Vienna");

        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getDisplayName(),
                saved.getStatus().name(),
                saved.getAccountType().name()
        );
    }
}
