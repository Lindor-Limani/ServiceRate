package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // Zwingend notwendig für M9 (Login/JWT)

    @Column(name = "account_type", nullable = false)
    private String accountType; // "CUSTOMER" oder "PROVIDER"

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(name = "payout_iban")
    private String payoutIban;

    @Column(name = "paypal_merchant_id")
    private String paypalMerchantId;

    @Column(name = "paypal_email")
    private String paypalEmail;

    @Column(name = "paypal_onboarding_status")
    private String paypalOnboardingStatus = "NOT_CONNECTED";

    @Column(name = "paypal_permissions_granted")
    private Boolean paypalPermissionsGranted;

    @Column(name = "paypal_email_confirmed")
    private Boolean paypalEmailConfirmed;

    @Column(name = "paypal_referral_self_url", length = 1000)
    private String paypalReferralSelfUrl;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "email_verification_expires_at")
    private OffsetDateTime emailVerificationExpiresAt;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private OffsetDateTime passwordResetExpiresAt;
}
