package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Sempre o total do pedido, calculado no servidor — nunca vem do cliente. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Identificador da cobrança (até 25 caracteres alfanuméricos), vai no BR Code e volta no webhook. */
    @Column(nullable = false, unique = true, length = 25)
    private String txid;

    /** BR Code "copia e cola"; o QR Code é a representação gráfica deste texto. */
    @Column(name = "pix_payload", nullable = false, length = 512)
    private String pixPayload;

    @Column(name = "end_to_end_id", length = 64)
    private String endToEndId;

    /** WEBHOOK ou ADMIN. */
    @Column(name = "confirmed_by", length = 20)
    private String confirmedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // null = entidade nova (o Spring Data usa persist); o Hibernate grava 0 no insert e incrementa a cada update
    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isOverdue(LocalDateTime now) {
        return isPending() && now.isAfter(expiresAt);
    }
}
