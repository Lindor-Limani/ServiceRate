package at.hcw.serviceratebackend.controller;

import at.hcw.serviceratebackend.dto.ChatMessageRequest;
import at.hcw.serviceratebackend.dto.ChatMessageResponse;
import at.hcw.serviceratebackend.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/booking/{bookingId}")
    public List<ChatMessageResponse> getMessages(@PathVariable UUID bookingId, Authentication authentication) {
        return chatMessageService.getMessages(bookingId, (String) authentication.getPrincipal());
    }

    @PostMapping("/booking/{bookingId}")
    public ChatMessageResponse sendMessage(
            @PathVariable UUID bookingId,
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication
    ) {
        return chatMessageService.sendMessage(bookingId, request, (String) authentication.getPrincipal());
    }
}
