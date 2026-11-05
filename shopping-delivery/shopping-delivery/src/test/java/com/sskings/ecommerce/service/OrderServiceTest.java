package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock AddressRepository addressRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @InjectMocks OrderService orderService;

    private User user;
    private Product product;
    private Address address;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("u@u.com").build();
        product = Product.builder().id(10L).name("Widget").price(BigDecimal.TEN).stockQuantity(5).build();
        address = Address.builder().id(1L).user(user).street("Main St").number("1")
                .neighborhood("Downtown").city("SP").state("SP").zipCode("01000-000").build();
        cartItem = CartItem.builder().id(1L).user(user).product(product).quantity(2).build();
    }

    @Test
    @DisplayName("checkout — empty cart throws BusinessException")
    void checkout_emptyCart_throws() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of());

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cart is empty");
    }

    @Test
    @DisplayName("checkout — address not found throws ResourceNotFoundException")
    void checkout_addressNotFound_throws() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("checkout — insufficient stock throws BusinessException")
    void checkout_insufficientStock_throws() {
        product.setStockQuantity(1);
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("checkout — success creates order, deducts stock, clears cart")
    void checkout_success() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        Order savedOrder = Order.builder().id(100L).user(user).address(address)
                .status(OrderStatus.PENDING).total(new BigDecimal("20.00"))
                .items(new ArrayList<>()).build();
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        OrderResponse resp = orderService.checkout(new CheckoutRequest(1L), 1L);

        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.status()).isEqualTo(OrderStatus.PENDING);
        then(cartItemRepository).should().deleteAllByUserId(1L);
        then(productRepository).should().save(product);
        assertThat(product.getStockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("findById — not found throws ResourceNotFoundException")
    void findById_notFound_throws() {
        given(orderRepository.findByIdAndUserIdWithItems(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAllByUser — returns list of orders")
    void findAllByUser_returnsList() {
        Order order = Order.builder().id(1L).user(user).address(address)
                .status(OrderStatus.PENDING).total(BigDecimal.TEN).items(List.of()).build();
        given(orderRepository.findAllByUserIdWithItems(1L)).willReturn(List.of(order));

        List<OrderResponse> orders = orderService.findAllByUser(1L);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).id()).isEqualTo(1L);
    }
}
