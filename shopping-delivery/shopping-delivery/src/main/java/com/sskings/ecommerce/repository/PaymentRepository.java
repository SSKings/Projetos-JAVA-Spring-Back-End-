package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.Payment;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxid(String txid);

    @Query("SELECT p FROM Payment p JOIN FETCH p.order o WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Payment> findByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAllByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime now);
}
