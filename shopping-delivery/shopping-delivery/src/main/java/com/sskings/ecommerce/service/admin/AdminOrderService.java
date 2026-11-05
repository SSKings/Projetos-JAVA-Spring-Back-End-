package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.service.PaymentService;
import com.sskings.ecommerce.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final PaymentService paymentService;
    private final com.sskings.ecommerce.service.AuditService auditService;
    private final java.time.Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> findAll(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findAllByStatus(status, pageable)
                : orderRepository.findAllBy(pageable);
        return page.map(AdminOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse findById(Long id) {
        return AdminOrderResponse.from(getOrder(id));
    }

    @Transactional
    public AdminOrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = getOrder(id);
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new BusinessException("Cannot change order status from " + current + " to " + newStatus);
        }
        if (!order.canTransitionTo(newStatus)) {
            throw new BusinessException("Order payment is pending");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            stockService.restore(order);
            paymentService.onOrderCancelled(order);
        }
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(java.time.LocalDateTime.now(clock));   // início do prazo de arrependimento
        }
        order.setStatus(newStatus);
        auditService.record(null, com.sskings.ecommerce.service.AuditService.ORDER_STATUS_CHANGED, "ORDER", id,
                current + " -> " + newStatus);
        return AdminOrderResponse.from(orderRepository.save(order));
    }

    /** Registra a devolução do Pix (estorno pendente ou arrependimento). */
    @Transactional
    public AdminOrderResponse refundPayment(Long id) {
        paymentService.refundByAdmin(id);
        return AdminOrderResponse.from(getOrder(id));
    }

    /** Confirmação manual do Pix (admin conferiu o extrato). O pedido vai para CONFIRMED. */
    @Transactional
    public AdminOrderResponse confirmPayment(Long id) {
        paymentService.confirmByAdmin(id);
        return AdminOrderResponse.from(getOrder(id));
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
