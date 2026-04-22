package at.hcw.serviceratebackend.model.entity;

import at.hcw.serviceratebackend.model.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "issuer_type", nullable = false)
    private String issuerType;

    @Column(name = "issuer_id", nullable = false)
    private UUID issuerId;

    @Column(name = "recipient_type", nullable = false)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "total_net", nullable = false)
    private Double totalNet;

    @Column(name = "vat_total", nullable = false)
    private Double vatTotal;

    @Column(name = "total_gross", nullable = false)
    private Double totalGross;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "pdf_document_id")
    private UUID pdfDocumentId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
