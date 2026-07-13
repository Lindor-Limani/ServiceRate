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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByBooking = new ConcurrentHashMap<>();

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

        String content = request.content() != null ? request.content().trim() : "";
        String imageDataUrl = request.imageDataUrl() != null ? request.imageDataUrl().trim() : "";
        String imageName = request.imageName() != null ? request.imageName().trim() : "";
        validatePayload(content, imageDataUrl);

        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setBooking(booking);
        message.setSender(sender);
        message.setContent(content);
        message.setImageDataUrl(imageDataUrl.isBlank() ? null : imageDataUrl);
        message.setImageName(imageName.isBlank() ? null : imageName);

        ChatMessageResponse response = toResponse(chatMessageRepository.save(message));
        emitMessage(response);
        return response;
    }

    @Transactional(readOnly = true)
    public SseEmitter streamMessages(UUID bookingId, String email) {
        Booking booking = findAccessibleBooking(bookingId, email);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emittersByBooking.computeIfAbsent(booking.getId(), ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(booking.getId(), emitter));
        emitter.onTimeout(() -> removeEmitter(booking.getId(), emitter));
        emitter.onError(error -> removeEmitter(booking.getId(), emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data("connected"));
        } catch (IOException e) {
            removeEmitter(booking.getId(), emitter);
        }
        return emitter;
    }

    private void validatePayload(String content, String imageDataUrl) {
        if (!hasMeaningfulContent(content) && imageDataUrl.isBlank()) {
            throw new IllegalArgumentException("Nachricht oder Bild erforderlich.");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("Nachricht ist zu lang.");
        }
        if (!imageDataUrl.isBlank()) {
            if (!imageDataUrl.startsWith("data:image/")) {
                throw new IllegalArgumentException("Bitte nur Bilder senden.");
            }
            if (imageDataUrl.length() > 1_500_000) {
                throw new IllegalArgumentException("Bild ist zu groß. Bitte kleineres Bild wählen.");
            }
        }
    }

    private boolean hasMeaningfulContent(String content) {
        return content != null && content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private void emitMessage(ChatMessageResponse response) {
        List<SseEmitter> emitters = emittersByBooking.getOrDefault(response.bookingId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("message").data(response));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(response.bookingId(), emitter);
            }
        }
    }

    private void removeEmitter(UUID bookingId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByBooking.get(bookingId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByBooking.remove(bookingId);
        }
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
                message.getImageDataUrl(),
                message.getImageName(),
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
