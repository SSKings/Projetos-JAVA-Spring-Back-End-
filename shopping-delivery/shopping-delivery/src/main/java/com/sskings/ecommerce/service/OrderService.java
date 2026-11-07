package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final AuditService auditService;
    private final java.time.Clock clock;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request, Long userId) {
        List<CartItem> cartItems = cartItemRepository.findAllByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException("Cart is empty");
        }
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        User user = userRepository.getReferenceById(userId);
        Order order = Order.builder()
                .user(user)
                .address(address)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        // Baixa o estoque SEMPRE em ordem de id do produto (como StockService.restore): checkout de [A, B] concorrendo
        // com o cancelamento de um pedido [B, A] travava as linhas em ordens opostas → deadlock no PostgreSQL → 500.
        // Achado no teste de carga (load-tests/prod-like); coberto por ConcurrencyIntegrationTest.
        cartItems = cartItems.stream().sorted(java.util.Comparator.comparing(ci -> ci.getProduct().getId())).toList();
        for (CartItem ci : cartItems) {
            // produto desativado depois de ir para o carrinho não pode ser comprado
            Product product = productRepository.findByIdAndActiveTrue(ci.getProduct().getId())
                    .orElseThrow(() -> new BusinessException("Product no longer available: " + ci.getProduct().getName()));
            // baixa atômica no banco; 0 linhas = sem estoque (rollback de tudo o que já foi baixado)
            if (productRepository.decrementStock(product.getId(), ci.getQuantity()) == 0) {
                throw new BusinessException("Insufficient stock for: " + product.getName());
            }

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .quantity(ci.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build());
            total = total.add(subtotal);
        }

        order.setTotal(total);
        Order saved = orderRepository.save(order);
        paymentService.createPixCharge(saved); // único método hoje: CheckoutRequest.paymentMethod = PIX
        cartItemRepository.deleteAllByUserId(userId);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAllByUser(Long userId) {
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id, Long userId) {
        return orderRepository.findByIdAndUserIdWithItems(id, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    /** Cancelamento pelo cliente antes do envio: devolve estoque; Pix pago vira estorno pendente. */
    @Transactional
    public OrderResponse cancelByCustomer(Long id, Long userId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!order.isCancellableByCustomer()) {
            throw new BusinessException("Order can no longer be cancelled");
        }
        stockService.restore(order);
        paymentService.onOrderCancelled(order);
        order.setStatus(OrderStatus.CANCELLED);
        auditService.record(order.getUser(), AuditService.ORDER_CANCELLED_BY_CUSTOMER, "ORDER", id,
                order.getPayment() != null ? "payment " + order.getPayment().getStatus() : null);
        return OrderResponse.from(orderRepository.save(order));
    }

    /** Direito de arrependimento (CDC art. 49): até 7 dias após a entrega, sem precisar justificar. */
    @Transactional
    public OrderResponse requestReturn(Long id, Long userId, String reason) {
        Order order = orderRepository.findByIdAndUserIdWithItems(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!order.canRequestReturn(java.time.LocalDateTime.now(clock))) {
            throw new BusinessException("Return period has expired or a return was already requested");
        }
        order.setReturnRequestedAt(java.time.LocalDateTime.now(clock));
        order.setReturnReason(reason == null || reason.isBlank() ? null : reason.trim());
        auditService.record(order.getUser(), AuditService.RETURN_REQUESTED, "ORDER", id, null);
        return OrderResponse.from(orderRepository.save(order));
    }
}
