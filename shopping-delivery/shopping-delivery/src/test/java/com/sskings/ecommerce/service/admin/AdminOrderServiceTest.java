package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.ProductRepository;
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

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks AdminOrderService service;

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
    }

    @Test
    @DisplayName("PENDING → CONFIRMED é permitido")
    void updateStatus_validTransition() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.updateStatus(1L, OrderStatus.CONFIRMED);

        assertThat(resp.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(resp.customerEmail()).isEqualTo("ana@a.com");
    }

    @Test
    @DisplayName("Cancelar pedido devolve os itens ao estoque")
    void updateStatus_cancel_restoresStock() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.updateStatus(1L, OrderStatus.CANCELLED);

        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("PENDING → DELIVERED é rejeitado")
    void updateStatus_invalidTransition_throws() {
        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.DELIVERED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot change order status");
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(List.of(order.getStatus())).containsExactly(OrderStatus.PENDING);
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
