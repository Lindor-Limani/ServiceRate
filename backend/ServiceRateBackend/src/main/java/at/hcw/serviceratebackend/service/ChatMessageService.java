package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.dto.ChatMessageRequest;
import at.hcw.serviceratebackend.dto.ChatMessageResponse;
import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.ChatMessage;
import at.hcw.serviceratebackend.model.entity.User;
import at.hcw.serviceratebackend.repository.BookingRepository;
import at.hcw.serviceratebackend.repository.ChatMessageRepository;
import at.hcw.serviceratebackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID bookingId, String email) {
        Booking booking = findAccessibleBooking(bookingId, email);
        return chatMessageRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(UUID bookingId, ChatMessageRequest request, String email) {
        Booking booking = findAccessibleBooking(bookingId, email);
        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));

        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setBooking(booking);
        message.setSender(sender);
        message.setContent(request.content().trim());

        return toResponse(chatMessageRepository.save(message));
    }

    private Booking findAccessibleBooking(UUID bookingId, String email) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Buchung nicht gefunden"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));

        UUID customerId = booking.getCustomer() != null ? booking.getCustomer().getId() : null;
        UUID providerId = booking.getServiceOffering() != null && booking.getServiceOffering().getProvider() != null
                ? booking.getServiceOffering().getProvider().getId()
                : null;

        if (!user.getId().equals(customerId) && !user.getId().equals(providerId)) {
            throw new IllegalArgumentException("Kein Zugriff auf diese Unterhaltung.");
        }
        return booking;
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getBooking().getId(),
                fullName(message.getSender()),
                message.getSender() != null ? message.getSender().getAccountType() : "UNKNOWN",
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private String fullName(User user) {
        if (user == null) return "Unbekannt";
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
