package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    List<PaymentMethod> findByUser_IdOrderByDefaultMethodDescCreatedAtDesc(UUID userId);
}
