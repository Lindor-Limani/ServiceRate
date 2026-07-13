package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "chat_messages")
public class ChatMessage extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "text")
    private String content;

    @Lob
    @Column(name = "image_data_url", columnDefinition = "text")
    private String imageDataUrl;

    @Column(name = "image_name")
    private String imageName;
}
