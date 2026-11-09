package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findAllByUserIdWithItems(@Param("userId") Long userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") Long id, @Param("userId") Long userId);

    // Admin
    @EntityGraph(attributePaths = "user")
    Page<Order> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "items"})
    Optional<Order> findWithItemsById(Long id);

    long countByStatus(OrderStatus status);
    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /** Unidades de um produto nos pedidos do cliente que ainda aguardam pagamento (revisão 4, R4-4). */
    @Query("select coalesce(sum(i.quantity), 0) from Order o join o.items i "
            + "where o.user.id = :userId and o.status = com.sskings.ecommerce.model.enums.OrderStatus.PENDING "
            + "and i.product.id = :productId")
    long sumPendingUnits(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Pix abandonados (expirados ou cancelados sem pagamento) do cliente desde um instante (revisão 4, R4-4). */
    @Query("select count(p) from Payment p where p.order.user.id = :userId and p.paidAt is null "
            + "and p.status in (com.sskings.ecommerce.model.enums.PaymentStatus.EXPIRED, "
            + "com.sskings.ecommerce.model.enums.PaymentStatus.CANCELLED) and p.createdAt > :since")
    long countAbandonedPaymentsSince(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);

    boolean existsByAddressId(Long addressId);

    boolean existsByUserIdAndStatusIn(Long userId, java.util.Collection<OrderStatus> statuses);

    /** Faturamento: pedidos não cancelados e já pagos (pedidos antigos, sem pagamento, contam como pagos). */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o LEFT JOIN o.payment p "
            + "WHERE o.status <> com.sskings.ecommerce.model.enums.OrderStatus.CANCELLED "
            + "AND (p IS NULL OR p.status = com.sskings.ecommerce.model.enums.PaymentStatus.PAID)")
    BigDecimal sumRevenue();
}
