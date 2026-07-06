package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PayPalService {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;
    private final String returnUrl;
    private final String cancelUrl;
    private final String sellerReturnUrl;
    private final String partnerAttributionId;
    private final String partnerMerchantId;
    private final String webBaseUrl;

    private String cachedAccessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalService(
            RestClient.Builder restClientBuilder,
            @Value("${paypal.client-id:}") String clientId,
            @Value("${paypal.client-secret:}") String clientSecret,
            @Value("${paypal.mode:sandbox}") String mode,
            @Value("${paypal.return-url:${app.frontend-base-url:http://localhost:5500}/customer-app.html}") String returnUrl,
            @Value("${paypal.cancel-url:${app.frontend-base-url:http://localhost:5500}/customer-app.html}") String cancelUrl,
            @Value("${paypal.seller-return-url:${app.frontend-base-url:http://localhost:5500}/provider-dashboard.html}") String sellerReturnUrl,
            @Value("${paypal.partner-attribution-id:}") String partnerAttributionId,
            @Value("${paypal.partner-merchant-id:}") String partnerMerchantId
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.baseUrl = "live".equalsIgnoreCase(mode)
                ? "https://api-m.paypal.com"
                : "https://api-m.sandbox.paypal.com";
        this.webBaseUrl = "live".equalsIgnoreCase(mode)
                ? "https://www.paypal.com"
                : "https://www.sandbox.paypal.com";
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.sellerReturnUrl = sellerReturnUrl;
        this.partnerAttributionId = partnerAttributionId == null ? "" : partnerAttributionId.trim();
        this.partnerMerchantId = partnerMerchantId == null ? "" : partnerMerchantId.trim();
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String createIdentityConnectLink(User provider) {
        requireConfigured();
        if (provider == null || provider.getId() == null) {
            throw new IllegalArgumentException("Provider fehlt.");
        }

        return webBaseUrl + "/connect"
                + "?flowEntry=static"
                + "&client_id=" + encode(clientId)
                + "&response_type=code"
                + "&scope=" + encode("openid profile email")
                + "&redirect_uri=" + encode(sellerReturnUrl)
                + "&state=" + encode(provider.getId().toString());
    }

    public PayPalIdentityProfile exchangeIdentityCode(String code, String redirectUri) {
        requireConfigured();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("PayPal-Code fehlt.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri == null || redirectUri.isBlank() ? sellerReturnUrl : redirectUri);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(baseUrl + "/v1/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal Identity-Code konnte nicht gegen ein Token getauscht werden (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        String accessToken = tokenResponse == null ? null : String.valueOf(tokenResponse.get("access_token"));
        if (accessToken == null || accessToken.isBlank() || "null".equals(accessToken)) {
            throw new IllegalStateException("PayPal Identity hat kein Access Token geliefert.");
        }

        Map<String, Object> userInfo;
        try {
            userInfo = restClient.get()
                    .uri(baseUrl + "/v1/identity/oauth2/userinfo?schema=paypalv1.1")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal UserInfo konnte nicht gelesen werden (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        return new PayPalIdentityProfile(
                findString(userInfo, "user_id", "payer_id", "sub"),
                findString(userInfo, "email"),
                findBoolean(userInfo, "email_verified", "verified_account")
        );
    }

    public PayPalOrder createOrder(Booking booking) {
        requireConfigured();

        ServiceOffering offering = booking.getServiceOffering();
        String currency = offering.getCurrencyCode() == null || offering.getCurrencyCode().isBlank()
                ? "EUR"
                : offering.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        String amount = amountFor(booking).toPlainString();

        Map<String, Object> response = restClient.post()
                .uri(baseUrl + "/v2/checkout/orders")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "intent", "CAPTURE",
                        "purchase_units", List.of(purchaseUnit(booking, offering, currency, amount)),
                        "application_context", Map.of(
                                "brand_name", "ServiceRate",
                                "user_action", "PAY_NOW",
                                "return_url", appendParams(returnUrl, booking, "success"),
                                "cancel_url", appendParams(cancelUrl, booking, "cancel")
                        )
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("PayPal konnte keine Order erstellen.");
        }

        String orderId = String.valueOf(response.get("id"));
        String approveUrl = approvalUrl(response);
        if (approveUrl == null || approveUrl.isBlank()) {
            throw new IllegalStateException("PayPal hat keine Approval-URL geliefert.");
        }
        return new PayPalOrder(orderId, approveUrl);
    }

    public PayPalCapture captureOrder(String orderId) {
        requireConfigured();

        Map<String, Object> response = restClient.post()
                .uri(baseUrl + "/v2/checkout/orders/{orderId}/capture", orderId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new IllegalStateException("PayPal Capture lieferte keine Antwort.");
        }

        String status = String.valueOf(response.getOrDefault("status", ""));
        String captureId = extractCaptureId(response);
        return new PayPalCapture(status, captureId);
    }

    public PayPalReferral createSellerOnboardingLink(User provider) {
        requireConfigured();
        if (provider == null || provider.getId() == null) {
            throw new IllegalArgumentException("Provider fehlt.");
        }

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/v2/customer/partner-referrals")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken());
                        if (!partnerAttributionId.isBlank()) {
                            headers.set("PayPal-Partner-Attribution-Id", partnerAttributionId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "tracking_id", provider.getId().toString(),
                            "operations", List.of(Map.of(
                                    "operation", "API_INTEGRATION",
                                    "api_integration_preference", Map.of(
                                            "rest_api_integration", Map.of(
                                                    "integration_method", "PAYPAL",
                                                    "integration_type", "THIRD_PARTY",
                                                    "third_party_details", Map.of(
                                                            "features", List.of("PAYMENT", "REFUND", "PARTNER_FEE")
                                                    )
                                            )
                                    )
                            )),
                            "products", List.of("PPCP"),
                            "legal_consents", List.of(Map.of(
                                    "type", "SHARE_DATA_CONSENT",
                                    "granted", true
                            )),
                            "partner_config_override", Map.of(
                                    "return_url", appendProviderReturnParams(provider),
                                    "return_url_description", "Zurueck zu ServiceRate",
                                    "show_add_credit_card", true
                            ),
                            "customer_data", customerData(provider)
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("PayPal Partner-Onboarding wurde abgelehnt: Client ID/Secret oder PAYPAL_MODE passen nicht zur Platform-App.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("PayPal Partner-Onboarding ist fuer diese App nicht freigeschaltet. Fuer Platform/PARTNER_FEE muss der PayPal-Account als Partner/Commerce-Platform berechtigt sein. PayPal Antwort: " + e.getResponseBodyAsString(), e);
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal Partner-Onboarding fehlgeschlagen (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        String actionUrl = linkByRel(response, "action_url");
        String selfUrl = linkByRel(response, "self");
        if (actionUrl == null || actionUrl.isBlank()) {
            throw new IllegalStateException("PayPal hat keinen Onboarding-Link geliefert.");
        }
        return new PayPalReferral(actionUrl, selfUrl);
    }

    public PayPalSellerStatus getSellerOnboardingStatus(String selfUrl) {
        requireConfigured();
        if (selfUrl == null || selfUrl.isBlank()) {
            throw new IllegalArgumentException("Es wurde noch kein PayPal-Onboarding-Link erzeugt.");
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(selfUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal-Onboarding-Status konnte nicht gelesen werden (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        return new PayPalSellerStatus(
                findString(response, "merchantIdInPayPal", "merchant_id", "merchantId", "payer_id"),
                findBoolean(response, "permissionsGranted", "permissions_granted"),
                findString(response, "accountStatus", "account_status"),
                findBoolean(response, "consentStatus", "consent_status"),
                findBoolean(response, "isEmailConfirmed", "is_email_confirmed", "email_confirmed")
        );
    }

    public PayPalSellerStatus getSellerOnboardingStatusByTrackingId(String trackingId) {
        requireConfigured();
        if (partnerMerchantId.isBlank()) {
            throw new IllegalStateException("PAYPAL_PARTNER_MERCHANT_ID fehlt. Ohne diese Plattform-Merchant-ID kann der Seller-Status nur ueber die Rueckkehr-URL gespeichert werden.");
        }
        if (trackingId == null || trackingId.isBlank()) {
            throw new IllegalArgumentException("Tracking-ID fehlt.");
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(baseUrl + "/v1/customer/partners/" + encode(partnerMerchantId)
                            + "/merchant-integrations?tracking_id=" + encode(trackingId))
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal-Seller-Status konnte nicht gelesen werden (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        Map<String, Object> seller = firstSellerStatus(response);
        return sellerStatusFromMerchantIntegration(seller);
    }

    public PayPalSellerStatus getSellerOnboardingStatusByMerchantId(String sellerMerchantId) {
        requireConfigured();
        if (partnerMerchantId.isBlank()) {
            throw new IllegalStateException("PAYPAL_PARTNER_MERCHANT_ID fehlt. Ohne diese Plattform-Merchant-ID kann der Seller-Status nur ueber die Rueckkehr-URL gespeichert werden.");
        }
        if (sellerMerchantId == null || sellerMerchantId.isBlank()) {
            throw new IllegalArgumentException("Seller Merchant ID fehlt.");
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(baseUrl + "/v1/customer/partners/" + encode(partnerMerchantId)
                            + "/merchant-integrations/" + encode(sellerMerchantId))
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("PayPal-Seller-Status konnte nicht gelesen werden (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        }

        return sellerStatusFromMerchantIntegration(response);
    }

    private String accessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedAccessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/v1/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("PayPal lehnt Client ID/Secret ab. Bitte pruefe PAYPAL_MODE, PAYPAL_CLIENT_ID und PAYPAL_CLIENT_SECRET: Sandbox-Credentials funktionieren nur mit PAYPAL_MODE=sandbox, Live-Credentials nur mit PAYPAL_MODE=live.", e);
        }

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("PayPal Access Token konnte nicht geholt werden.");
        }

        cachedAccessToken = String.valueOf(response.get("access_token"));
        long expiresIn = Long.parseLong(String.valueOf(response.getOrDefault("expires_in", "300")));
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedAccessToken;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("PayPal ist nicht konfiguriert. Bitte PAYPAL_CLIENT_ID und PAYPAL_CLIENT_SECRET setzen.");
        }
    }

    private BigDecimal amountFor(Booking booking) {
        double basePrice = booking.getServiceOffering() == null || booking.getServiceOffering().getPrice() == null
                ? 0.0
                : booking.getServiceOffering().getPrice();
        double hours = booking.getActualHours() != null && booking.getActualHours() > 0
                ? booking.getActualHours()
                : 1.0;
        return BigDecimal.valueOf(basePrice)
                .multiply(BigDecimal.valueOf(hours))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> customerData(User provider) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (provider.getEmail() != null && !provider.getEmail().isBlank()) {
            data.put("email", provider.getEmail());
        }
        if ((provider.getFirstName() != null && !provider.getFirstName().isBlank())
                || (provider.getLastName() != null && !provider.getLastName().isBlank())) {
            data.put("name", Map.of(
                    "given_name", provider.getFirstName() == null ? "" : provider.getFirstName(),
                    "surname", provider.getLastName() == null ? "" : provider.getLastName()
            ));
        }
        return data;
    }

    private String appendProviderReturnParams(User provider) {
        String separator = sellerReturnUrl.contains("?") ? "&" : "?";
        return sellerReturnUrl + separator + "paypalOnboarding=return&providerId=" + provider.getId();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> purchaseUnit(Booking booking, ServiceOffering offering, String currency, String amount) {
        Map<String, Object> unit = new java.util.LinkedHashMap<>();
        unit.put("reference_id", booking.getId().toString());
        unit.put("description", safeDescription(offering));
        unit.put("custom_id", booking.getId().toString());
        unit.put("amount", Map.of(
                "currency_code", currency,
                "value", amount
        ));

        UserPayee payee = providerPayee(offering);
        if (payee != null) {
            unit.put("payee", payee.asMap());
        }

        if (booking.getPlatformFeeAmount() != null && booking.getPlatformFeeAmount() > 0) {
            unit.put("payment_instruction", Map.of(
                    "platform_fees", List.of(Map.of(
                            "amount", Map.of(
                                    "currency_code", currency,
                                    "value", BigDecimal.valueOf(booking.getPlatformFeeAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString()
                            )
                    ))
            ));
        }

        return unit;
    }

    private UserPayee providerPayee(ServiceOffering offering) {
        if (offering == null || offering.getProvider() == null) {
            return null;
        }
        String merchantId = offering.getProvider().getPaypalMerchantId();
        if (merchantId != null && !merchantId.isBlank()) {
            return new UserPayee("merchant_id", merchantId.trim());
        }
        String email = offering.getProvider().getPaypalEmail();
        if (email != null && !email.isBlank()) {
            return new UserPayee("email_address", email.trim());
        }
        return null;
    }

    private String safeDescription(ServiceOffering offering) {
        String title = offering == null || offering.getTitle() == null ? "ServiceRate Buchung" : offering.getTitle();
        return title.length() > 127 ? title.substring(0, 127) : title;
    }

    private String appendParams(String url, Booking booking, String status) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "paypal=" + status + "&bookingId=" + booking.getId();
    }

    @SuppressWarnings("unchecked")
    private String approvalUrl(Map<String, Object> response) {
        return linkByRel(response, "approve");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstSellerStatus(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object integrations = response.get("merchant_integrations");
        if (integrations instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return (Map<String, Object>) first;
        }
        return response;
    }

    private PayPalSellerStatus sellerStatusFromMerchantIntegration(Map<String, Object> seller) {
        Boolean paymentsReceivable = findBoolean(seller, "payments_receivable", "permissionsGranted", "permissions_granted");
        Boolean oauthGranted = oauthThirdPartyGranted(seller);
        Boolean permissionsGranted = Boolean.TRUE.equals(paymentsReceivable)
                && (oauthGranted == null || Boolean.TRUE.equals(oauthGranted));

        return new PayPalSellerStatus(
                findString(seller, "merchant_id", "merchantIdInPayPal", "merchantId", "payer_id"),
                permissionsGranted,
                findString(seller, "account_status", "accountStatus"),
                findBoolean(seller, "consent_status", "consentStatus"),
                findBoolean(seller, "primary_email_confirmed", "isEmailConfirmed", "is_email_confirmed", "email_confirmed")
        );
    }

    private Boolean oauthThirdPartyGranted(Object value) {
        Object oauthThirdParty = findValue(value, "oauth_third_party");
        if (oauthThirdParty instanceof List<?> list) {
            return !list.isEmpty();
        }
        Object oauthIntegrations = findValue(value, "oauth_integrations");
        if (oauthIntegrations instanceof List<?> list) {
            return !list.isEmpty();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String linkByRel(Map<String, Object> response, String rel) {
        if (response == null) {
            return null;
        }
        Object linksValue = response.get("links");
        if (!(linksValue instanceof List<?> links)) {
            return null;
        }
        for (Object linkValue : links) {
            if (linkValue instanceof Map<?, ?> link && rel.equals(link.get("rel"))) {
                return String.valueOf(link.get("href"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractCaptureId(Map<String, Object> response) {
        Object purchaseUnitsValue = response.get("purchase_units");
        if (!(purchaseUnitsValue instanceof List<?> purchaseUnits) || purchaseUnits.isEmpty()) {
            return null;
        }
        Object firstUnit = purchaseUnits.get(0);
        if (!(firstUnit instanceof Map<?, ?> purchaseUnit)) {
            return null;
        }
        Object paymentsValue = purchaseUnit.get("payments");
        if (!(paymentsValue instanceof Map<?, ?> payments)) {
            return null;
        }
        Object capturesValue = payments.get("captures");
        if (!(capturesValue instanceof List<?> captures) || captures.isEmpty()) {
            return null;
        }
        Object firstCapture = captures.get(0);
        if (firstCapture instanceof Map<?, ?> capture && capture.get("id") != null) {
            return String.valueOf(capture.get("id"));
        }
        return null;
    }

    public record PayPalOrder(String orderId, String approveUrl) {}
    public record PayPalCapture(String status, String captureId) {}
    public record PayPalReferral(String actionUrl, String selfUrl) {}
    public record PayPalSellerStatus(String merchantIdInPayPal, Boolean permissionsGranted, String accountStatus, Boolean consentStatus, Boolean isEmailConfirmed) {}
    public record PayPalIdentityProfile(String accountId, String email, Boolean emailConfirmed) {}

    private record UserPayee(String key, String value) {
        Map<String, String> asMap() {
            return Map.of(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private String findString(Object value, String... keys) {
        Object found = findValue(value, keys);
        return found == null ? null : String.valueOf(found);
    }

    private Boolean findBoolean(Object value, String... keys) {
        Object found = findValue(value, keys);
        if (found instanceof Boolean bool) {
            return bool;
        }
        if (found == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(found));
    }

    private Object findValue(Object value, String... keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
            for (Object nested : map.values()) {
                Object found = findValue(nested, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object nested : list) {
                Object found = findValue(nested, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
