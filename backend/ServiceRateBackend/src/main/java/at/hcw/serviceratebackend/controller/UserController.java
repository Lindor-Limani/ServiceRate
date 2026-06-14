package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.CreateUserRequest;
import at.hcw.serviceratebackend.dto.UpdateUserRequest;
import at.hcw.serviceratebackend.dto.UserResponse;
import at.hcw.serviceratebackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    // Ein User darf ausschließlich sein EIGENES Konto lesen/ändern/löschen.
    // Geprüft wird über das JWT-Subject (E-Mail) -> zugehörige User-ID muss der Pfad-ID entsprechen.

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id, Authentication auth) {
        if (!isSelf(id, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               Authentication auth) {
        if (!isSelf(id, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        if (!isSelf(id, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // true, wenn das eingeloggte Token-Subject zur angefragten User-ID gehört
    private boolean isSelf(UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return false;
        return userService.findIdByEmail((String) auth.getPrincipal())
                .map(currentId -> currentId.equals(id))
                .orElse(false);
    }
}
