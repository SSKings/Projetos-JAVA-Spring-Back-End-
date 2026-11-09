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

    boolean existsByAddressId(Long addressId);

    boolean existsByUserIdAndStatusIn(Long userId, java.util.Collection<OrderStatus> statuses);

    /** Faturamento: pedidos não cancelados e já pagos (pedidos antigos, sem pagamento, contam como pagos). */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o LEFT JOIN o.payment p "
            + "WHERE o.status <> com.sskings.ecommerce.model.enums.OrderStatus.CANCELLED "
            + "AND (p IS NULL OR p.status = com.sskings.ecommerce.model.enums.PaymentStatus.PAID)")
    BigDecimal sumRevenue();
}
