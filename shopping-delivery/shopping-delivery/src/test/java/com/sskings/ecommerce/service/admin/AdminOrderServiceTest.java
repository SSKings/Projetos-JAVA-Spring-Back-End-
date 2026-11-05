package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.service.PaymentService;
import com.sskings.ecommerce.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sskings.ecommerce.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.mockito.Mockito.lenient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock PaymentService paymentService;
    @Mock com.sskings.ecommerce.service.AuditService auditService;
    @InjectMocks AdminOrderService service;
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-05T12:00:00Z");

    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(1L).name("Caneca").price(BigDecimal.TEN).stockQuantity(3).build();
        User user = User.builder().id(7L).name("Ana").email("ana@a.com").build();
        Address address = Address.builder().id(1L).street("Rua A").neighborhood("Centro")
                .city("SP").state("SP").zipCode("01000-000").build();
        order = Order.builder().id(1L).user(user).address(address)
                .status(OrderStatus.PENDING).total(new BigDecimal("20")).items(new ArrayList<>()).build();
        order.getItems().add(OrderItem.builder().id(1L).order(order).product(product)
                .productName("Caneca").quantity(2).unitPrice(BigDecimal.TEN).subtotal(new BigDecimal("20")).build());
        lenient().when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        // StockService real (com o ProductRepository mockado) para verificar a devolução do estoque
        service = new AdminOrderService(orderRepository, new StockService(productRepository), paymentService, auditService,
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private void withPayment(PaymentStatus status) {
        order.setPayment(Payment.builder().order(order).method(PaymentMethod.PIX).status(status)
                .amount(order.getTotal()).txid("TX1").pixPayload("000201")
                .createdAt(java.time.LocalDateTime.now()).expiresAt(java.time.LocalDateTime.now().plusMinutes(30)).build());
    }

    @Test
    @DisplayName("PENDING → CONFIRMED é permitido")
    void updateStatus_validTransition() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.updateStatus(1L, OrderStatus.CONFIRMED);

        assertThat(resp.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(resp.nextStatuses()).containsExactly(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        assertThat(resp.customerEmail()).isEqualTo("ana@a.com");
    }

    @Test
    @DisplayName("Cancelar pedido devolve os itens ao estoque")
    void updateStatus_cancel_restoresStock() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.updateStatus(1L, OrderStatus.CANCELLED);

        verify(productRepository).incrementStock(1L, 2);   // UPDATE atômico, não read-modify-write
        verify(paymentService).onOrderCancelled(order);
        verify(auditService).record(null, com.sskings.ecommerce.service.AuditService.ORDER_STATUS_CHANGED, "ORDER", 1L, "PENDING -> CANCELLED");
    }

    @Test
    @DisplayName("Pix pendente: pedido não pode ser confirmado/enviado/entregue, só cancelado")
    void updateStatus_awaitingPayment() {
        withPayment(PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(BusinessException.class).hasMessage("Order payment is pending");
        assertThat(service.findById(1L).nextStatuses()).containsExactly(OrderStatus.CANCELLED);
        assertThat(service.findById(1L).payment().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Pix pago libera o fluxo normal")
    void updateStatus_paid() {
        withPayment(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        assertThat(service.updateStatus(1L, OrderStatus.SHIPPED).status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("Confirmação manual do Pix delega ao PaymentService e devolve o pedido atualizado")
    void confirmPayment() {
        var resp = service.confirmPayment(1L);

        verify(paymentService).confirmByAdmin(1L);
        assertThat(resp.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Registro de estorno delega ao PaymentService")
    void refundPayment() {
        assertThat(service.refundPayment(1L).id()).isEqualTo(1L);
        verify(paymentService).refundByAdmin(1L);
    }

    @Test
    @DisplayName("PENDING → DELIVERED (pulando etapas) é permitido e não mexe no estoque")
    void updateStatus_skipSteps() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.updateStatus(1L, OrderStatus.DELIVERED);

        assertThat(resp.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(resp.nextStatuses()).isEmpty();
        assertThat(resp.deliveredAt()).isEqualTo(java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC)); // início do prazo de arrependimento
        verify(productRepository, never()).incrementStock(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("SHIPPED → CANCELLED é rejeitado e o estoque não muda")
    void updateStatus_invalidTransition_throws() {
        order.setStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.CANCELLED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot change order status from SHIPPED to CANCELLED");
        verify(productRepository, never()).incrementStock(any(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pedido final (DELIVERED) não pode voltar")
    void updateStatus_fromFinalState_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.PENDING)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Lista todos ou filtra por status")
    void findAll_withAndWithoutStatus() {
        var pageable = PageRequest.of(0, 10);
        given(orderRepository.findAllBy(pageable)).willReturn(new PageImpl<>(List.of(order)));
        given(orderRepository.findAllByStatus(OrderStatus.PENDING, pageable)).willReturn(new PageImpl<>(List.of(order)));

        assertThat(service.findAll(null, pageable).getContent()).singleElement().extracting("customerName").isEqualTo("Ana");
        assertThat(service.findAll(OrderStatus.PENDING, pageable).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Detalhe e pedido inexistente")
    void findById() {
        assertThat(service.findById(1L).items()).hasSize(1);
        given(orderRepository.findWithItemsById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
