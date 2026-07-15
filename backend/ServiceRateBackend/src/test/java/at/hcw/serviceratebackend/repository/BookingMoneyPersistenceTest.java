package at.hcw.serviceratebackend.repository;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookingMoneyPersistenceTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsMarketplaceAmountsAsExactScaleTwoDecimals() {
        User provider = saveUser("provider-money@example.com", "PROVIDER");
        User customer = saveUser("customer-money@example.com", "CUSTOMER");

        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Dezimaler Service");
        offering.setDescription("Persistenztest");
        offering.setCategory("REPAIR");
        offering.setPrice(new BigDecimal("10.00"));
        offering.setStatus("ACTIVE");
        offering.setDeliverableType("ON_SITE");
        offering = serviceOfferingRepository.saveAndFlush(offering);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setServiceOffering(offering);
        booking.setServiceDate(OffsetDateTime.now().plusDays(1));
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStatus("ACCEPTED");
        booking.setBookedUnitPrice(new BigDecimal("10.00"));
        booking.setBookingCurrencyCode("EUR");
        booking.setGrossAmount(new BigDecimal("3.40"));
        booking.setPlatformFeeAmount(new BigDecimal("0.26"));
        booking.setProviderReceivableAmount(new BigDecimal("3.14"));

        UUID bookingId = bookingRepository.saveAndFlush(booking).getId();
        entityManager.clear();
        Booking persisted = bookingRepository.findById(bookingId).orElseThrow();

        assertThat(persisted.getGrossAmount()).isEqualByComparingTo("3.40");
        assertThat(persisted.getPlatformFeeAmount()).isEqualByComparingTo("0.26");
        assertThat(persisted.getProviderReceivableAmount()).isEqualByComparingTo("3.14");
        assertThat(persisted.getPlatformFeeAmount().add(persisted.getProviderReceivableAmount()))
                .isEqualByComparingTo(persisted.getGrossAmount());
        assertThat(persisted.getGrossAmount().scale()).isEqualTo(2);
    }

    private User saveUser(String email, String accountType) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Money");
        user.setLastName("Tester");
        user.setAccountType(accountType);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }
}
