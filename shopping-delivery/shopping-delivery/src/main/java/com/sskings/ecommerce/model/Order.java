package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Lock otimista: webhook, job de expiração, admin e cliente não sobrescrevem um ao outro. */
    // null = entidade nova (o Spring Data usa persist); o Hibernate grava 0 no insert e incrementa a cada update
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Direito de arrependimento (CDC art. 49): pedido de devolução em até 7 dias do recebimento. */
    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    public static final int RETURN_WINDOW_DAYS = 7;

    /** Cliente pode cancelar sozinho enquanto o pedido não foi enviado. */
    public boolean isCancellableByCustomer() {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }

    public LocalDateTime returnDeadline() {
        return deliveredAt == null ? null : deliveredAt.plusDays(RETURN_WINDOW_DAYS);
    }

    public boolean canRequestReturn(LocalDateTime now) {
        return status == OrderStatus.DELIVERED && returnRequestedAt == null
                && deliveredAt != null && !now.isAfter(returnDeadline());
    }

    /** null em pedidos criados antes do Pagamento existir (V4). */
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    /** Pedido com Pix ainda não pago não pode ser confirmado, enviado nem entregue — só cancelado. */
    public boolean isAwaitingPayment() {
        return payment != null && payment.getStatus() != PaymentStatus.PAID;
    }

    public boolean canTransitionTo(OrderStatus next) {
        return status.canTransitionTo(next) && (next == OrderStatus.CANCELLED || !isAwaitingPayment());
    }

    /** Transições permitidas considerando o pagamento (vai em AdminOrderResponse.nextStatuses). */
    public List<OrderStatus> nextStatuses() {
        return status.nextStatuses().stream().filter(this::canTransitionTo).toList();
    }
}
