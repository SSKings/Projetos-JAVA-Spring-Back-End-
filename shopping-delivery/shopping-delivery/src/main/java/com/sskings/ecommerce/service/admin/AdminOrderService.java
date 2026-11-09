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
    private final com.sskings.ecommerce.repository.UserRepository userRepository;
    private final com.sskings.ecommerce.service.AccountService accountService;
    private final org.springframework.context.ApplicationEventPublisher events;

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
    /**
     * Registrar estorno manual exige step-up (revisão 5, R5-2) e avisa todos os admins: marcar como devolvido um Pix
     * que não foi devolvido esconde dinheiro de cliente.
     */
    public AdminOrderResponse refundPayment(Long id, Long adminId, com.sskings.ecommerce.service.StepUp stepUp) {
        stepUp(adminId, stepUp);
        paymentService.refundByAdmin(id);
        Order order = getOrder(id);
        alert("Estorno do Pix do pedido #" + id + " (" + order.getTotal() + ") registrado manualmente", adminId);
        return AdminOrderResponse.from(order);
    }

    /** Confirmação manual do Pix (admin conferiu o extrato). O pedido vai para CONFIRMED. */
    @Transactional
    /**
     * Confirmar Pix manualmente exige step-up (revisão 5, R5-2): com só o token de admin, o atacante fazia um pedido,
     * "confirmava" o Pix sem pagar e o pedido seguia para envio. Todos os admins são avisados.
     */
    public AdminOrderResponse confirmPayment(Long id, Long adminId, com.sskings.ecommerce.service.StepUp stepUp) {
        stepUp(adminId, stepUp);
        paymentService.confirmByAdmin(id);
        Order order = getOrder(id);
        alert("Pix do pedido #" + id + " (" + order.getTotal() + ") confirmado manualmente, sem aviso do banco", adminId);
        return AdminOrderResponse.from(order);
    }

    private void stepUp(Long adminId, com.sskings.ecommerce.service.StepUp stepUp) {
        com.sskings.ecommerce.model.User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + adminId));
        accountService.verifyStepUp(admin, stepUp);
    }

    private void alert(String what, Long adminId) {
        events.publishEvent(new com.sskings.ecommerce.event.SecurityAlertEvent(
                what + " pelo admin #" + adminId + ". Confira no extrato; se não foi você, troque a senha agora."));
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
