package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.CreatePaymentMethodRequest;
import at.hcw.serviceratebackend.dto.PaymentMethodResponse;
import at.hcw.serviceratebackend.model.entity.PaymentMethod;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.PaymentMethodRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getMine(String email) {
        User user = findUser(email);
        return paymentMethodRepository.findByUser_IdOrderByDefaultMethodDescCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PaymentMethodResponse create(String email, CreatePaymentMethodRequest request) {
        User user = findUser(email);
        validate(request);

        if (Boolean.TRUE.equals(request.defaultMethod())) {
            paymentMethodRepository.findByUser_IdOrderByDefaultMethodDescCreatedAtDesc(user.getId())
                    .forEach(method -> method.setDefaultMethod(false));
        }

        PaymentMethod method = new PaymentMethod();
        method.setId(UUID.randomUUID());
        method.setUser(user);
        method.setBrand(request.brand().trim());
        method.setLast4(request.last4().trim());
        method.setHolderName(trimOrNull(request.holderName()));
        method.setExpiryMonth(request.expiryMonth());
        method.setExpiryYear(request.expiryYear());
        method.setProviderToken(request.providerToken().trim());
        method.setDefaultMethod(Boolean.TRUE.equals(request.defaultMethod()));

        return toResponse(paymentMethodRepository.save(method));
    }

    @Transactional
    public PaymentMethodResponse update(String email, UUID id, CreatePaymentMethodRequest request) {
        User user = findUser(email);
        validate(request);
        PaymentMethod method = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahlungsmethode nicht gefunden"));
        if (!method.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Kein Zugriff auf diese Zahlungsmethode.");
        }
        if (Boolean.TRUE.equals(request.defaultMethod())) {
            paymentMethodRepository.findByUser_IdOrderByDefaultMethodDescCreatedAtDesc(user.getId())
                    .forEach(existing -> existing.setDefaultMethod(false));
        }
        method.setBrand(request.brand().trim());
        method.setLast4(request.last4().trim());
        method.setHolderName(trimOrNull(request.holderName()));
        method.setExpiryMonth(request.expiryMonth());
        method.setExpiryYear(request.expiryYear());
        method.setProviderToken(request.providerToken().trim());
        method.setDefaultMethod(Boolean.TRUE.equals(request.defaultMethod()));
        return toResponse(paymentMethodRepository.save(method));
    }

    @Transactional
    public void delete(String email, UUID id) {
        User user = findUser(email);
        PaymentMethod method = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahlungsmethode nicht gefunden"));
        if (!method.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Kein Zugriff auf diese Zahlungsmethode.");
        }
        paymentMethodRepository.delete(method);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
    }

    private void validate(CreatePaymentMethodRequest request) {
        if (request.brand() == null || request.brand().isBlank()) throw new IllegalArgumentException("Kartenmarke fehlt.");
        if (request.last4() == null || !request.last4().matches("\\d{4}")) throw new IllegalArgumentException("Ungültige Kartenmetadaten.");
        if (request.providerToken() == null || request.providerToken().isBlank()) throw new IllegalArgumentException("Payment-Token fehlt.");
        if (request.expiryMonth() == null || request.expiryMonth() < 1 || request.expiryMonth() > 12) throw new IllegalArgumentException("Ungültiger Ablaufmonat.");
        if (request.expiryYear() == null || request.expiryYear() < 2026) throw new IllegalArgumentException("Ungültiges Ablaufjahr.");
    }

    private PaymentMethodResponse toResponse(PaymentMethod method) {
        return new PaymentMethodResponse(
                method.getId(),
                method.getBrand(),
                method.getLast4(),
                method.getHolderName(),
                method.getExpiryMonth(),
                method.getExpiryYear(),
                method.isDefaultMethod()
        );
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
