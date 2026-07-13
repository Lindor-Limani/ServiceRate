package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.BookingResponse;
import at.hcw.serviceratebackend.model.common.exception.GlobalExceptionHandler;
import at.hcw.serviceratebackend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createBooking_passesAuthenticatedCustomerEmailToService() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        when(bookingService.createBooking(any(), eq("customer@example.com")))
                .thenReturn(bookingResponse(bookingId, "PENDING"));

        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerId": "%s",
                                  "serviceOfferingId": "%s",
                                  "bookingDate": "2026-08-01"
                                }
                                """.formatted(UUID.randomUUID(), serviceId))
                        .principal(authentication("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingService).createBooking(any(), eq("customer@example.com"));
    }

    @Test
    void getMyProviderBookings_returnsProviderScopedBookings() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBookingsForProviderEmail("provider@example.com"))
                .thenReturn(List.of(bookingResponse(bookingId, "ACCEPTED")));

        mockMvc.perform(get("/api/bookings/provider/me")
                        .principal(authentication("provider@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void openDelivery_redirectsToResolvedDeliveryUrl() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.resolveDeliveryUrl(bookingId, "customer@example.com"))
                .thenReturn("https://files.example.com/result.pdf");

        mockMvc.perform(get("/api/bookings/{id}/delivery/open", bookingId)
                        .principal(authentication("customer@example.com")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://files.example.com/result.pdf"));
    }

    @Test
    void getDeliveryUrl_returnsResolvedUrlAsJson() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.resolveDeliveryUrl(bookingId, "customer@example.com"))
                .thenReturn("https://files.example.com/result.pdf");

        mockMvc.perform(get("/api/bookings/{id}/delivery/url", bookingId)
                        .principal(authentication("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://files.example.com/result.pdf"));
    }

    private Authentication authentication(String email) {
        return UsernamePasswordAuthenticationToken.authenticated(email, null, List.of());
    }

    private BookingResponse bookingResponse(UUID id, String status) {
        return new BookingResponse(
                id,
                "Customer User",
                null,
                "Provider User",
                null,
                UUID.randomUUID(),
                "Service",
                100.0,
                "REPAIR",
                null,
                false,
                status,
                LocalDate.of(2026, 8, 1),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "UNPAID",
                null,
                "MANUAL",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "NOT_READY",
                null,
                false,
                false,
                true,
                List.of(),
                null
        );
    }
}
