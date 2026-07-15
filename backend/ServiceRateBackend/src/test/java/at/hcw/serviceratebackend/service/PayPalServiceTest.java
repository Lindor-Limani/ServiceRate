package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalServiceTest {

    private static final String CHECKOUT_ERROR =
            "PayPal-Checkout ist für diesen Anbieter nicht vollständig verifiziert.";

    @Test
    void createSellerOnboardingLink_putsStateButNoProviderIdIntoReturnUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalService service = new PayPalService(
                builder,
                "client-id",
                "client-secret",
                "sandbox",
                "https://frontend.example/customer",
                "https://frontend.example/customer",
                "https://frontend.example/provider?source=paypal",
                "partner-attribution",
                "platform-merchant"
        );
        User provider = new User();
        provider.setId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        provider.setEmail("provider@example.com");

        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"access-token","expires_in":300}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/customer/partner-referrals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(
                        "https://frontend.example/provider?source=paypal&paypalOnboarding=return&state=secure-state"
                )))
                .andExpect(content().string(not(containsString("providerId"))))
                .andRespond(withSuccess("""
                        {
                          "links": [
                            {"rel":"action_url","href":"https://paypal.example/action"},
                            {"rel":"self","href":"https://paypal.example/self"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalService.PayPalReferral referral = service.createSellerOnboardingLink(provider, "secure-state");

        assertThat(referral.actionUrl()).isEqualTo("https://paypal.example/action");
        assertThat(referral.selfUrl()).isEqualTo("https://paypal.example/self");
        server.verify();
    }

    @Test
    void createOrder_routesPaymentOnlyToVerifiedProviderMerchantId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalService service = payPalService(builder);
        User provider = verifiedProvider();
        provider.setPaypalMerchantId(" verified-merchant ");
        provider.setPaypalEmail("legacy-fallback@example.com");
        Booking booking = booking(provider);

        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"access-token","expires_in":300}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "servicerate-order-" + booking.getId()))
                .andExpect(content().string(containsString("\"payee\":{\"merchant_id\":\"verified-merchant\"}")))
                .andExpect(content().string(not(containsString("email_address"))))
                .andExpect(content().string(not(containsString("legacy-fallback@example.com"))))
                .andRespond(withSuccess("""
                        {
                          "id":"ORDER-1",
                          "links":[{"rel":"approve","href":"https://paypal.example/approve"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        PayPalService.PayPalOrder order = service.createOrder(booking);

        assertThat(order.orderId()).isEqualTo("ORDER-1");
        assertThat(order.approveUrl()).isEqualTo("https://paypal.example/approve");
        server.verify();
    }

    @Test
    void createOrder_rejectsMissingBookingIdBeforePayPalCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalService service = payPalService(builder);
        Booking booking = booking(verifiedProvider());
        booking.setId(null);

        assertThatThrownBy(() -> service.createOrder(booking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Für die PayPal-Order ist eine persistierte Buchung erforderlich.");

        server.verify();
    }

    @Test
    void createOrder_rejectsEveryIncompleteProviderVerificationBeforePayPalCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PayPalService service = payPalService(builder);

        User actionRequired = verifiedProvider();
        actionRequired.setPaypalOnboardingStatus("ACTION_REQUIRED");
        User permissionsMissing = verifiedProvider();
        permissionsMissing.setPaypalPermissionsGranted(null);
        User permissionsDenied = verifiedProvider();
        permissionsDenied.setPaypalPermissionsGranted(false);
        User emailConfirmationMissing = verifiedProvider();
        emailConfirmationMissing.setPaypalEmailConfirmed(null);
        User emailUnconfirmed = verifiedProvider();
        emailUnconfirmed.setPaypalEmailConfirmed(false);
        User emailOnly = verifiedProvider();
        emailOnly.setPaypalMerchantId(" ");
        emailOnly.setPaypalEmail("legacy-only@example.com");

        for (User provider : new User[]{
                null,
                actionRequired,
                permissionsMissing,
                permissionsDenied,
                emailConfirmationMissing,
                emailUnconfirmed,
                emailOnly
        }) {
            assertThatThrownBy(() -> service.createOrder(booking(provider)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(CHECKOUT_ERROR);
        }
        assertThatThrownBy(() -> service.createOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Für die PayPal-Order ist eine persistierte Buchung erforderlich.");

        server.verify();
    }

    private PayPalService payPalService(RestClient.Builder builder) {
        return new PayPalService(
                builder,
                "client-id",
                "client-secret",
                "sandbox",
                "https://frontend.example/customer",
                "https://frontend.example/customer",
                "https://frontend.example/provider",
                "partner-attribution",
                "platform-merchant"
        );
    }

    private User verifiedProvider() {
        User provider = new User();
        provider.setId(UUID.randomUUID());
        provider.setPaypalMerchantId("verified-merchant");
        provider.setPaypalOnboardingStatus("CONNECTED");
        provider.setPaypalPermissionsGranted(true);
        provider.setPaypalEmailConfirmed(true);
        return provider;
    }

    private Booking booking(User provider) {
        ServiceOffering offering = new ServiceOffering();
        offering.setId(UUID.randomUUID());
        offering.setProvider(provider);
        offering.setTitle("Verified service");
        offering.setPrice(100.0);
        offering.setCurrencyCode("EUR");

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setServiceOffering(offering);
        return booking;
    }
}
