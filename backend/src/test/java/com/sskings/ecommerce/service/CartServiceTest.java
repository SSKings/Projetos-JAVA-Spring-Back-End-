package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.AddCartItemRequest;
import com.sskings.ecommerce.dto.request.UpdateCartItemRequest;
import com.sskings.ecommerce.dto.response.CartSummaryResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.CartItem;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.CartItemRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @InjectMocks CartService cartService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("u@u.com").build();
        product = Product.builder().id(10L).name("Widget").price(BigDecimal.TEN)
                .stockQuantity(5).active(true).build();
        // addItem trava o usuário antes de tudo (corrida no carrinho); updateItem/removeItem não usam
        lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("getCart returns summary for user")
    void getCart_returnsSummary() {
        CartItem ci = CartItem.builder().id(1L).user(user).product(product).quantity(2).build();
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(ci));

        CartSummaryResponse summary = cartService.getCart(1L);

        assertThat(summary.itemCount()).isEqualTo(1);
        assertThat(summary.total()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("getCart returns empty summary when cart is empty")
    void getCart_empty_returnsZeroTotal() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of());

        CartSummaryResponse summary = cartService.getCart(1L);

        assertThat(summary.itemCount()).isEqualTo(0);
        assertThat(summary.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("addItem — new item is added to cart")
    void addItem_newItem_added() {
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.empty());
        given(cartItemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        CartItem ci = CartItem.builder().id(1L).user(user).product(product).quantity(2).build();
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(ci));

        CartSummaryResponse summary = cartService.addItem(new AddCartItemRequest(10L, 2), 1L);

        assertThat(summary.itemCount()).isEqualTo(1);
        then(cartItemRepository).should().save(any(CartItem.class));
    }

    @Test
    @DisplayName("addItem — trava o usuário ANTES de procurar o item (senão duas adições simultâneas inserem as duas)")
    void addItem_locksUserBeforeLookingUpItem() {
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.empty());

        cartService.addItem(new AddCartItemRequest(10L, 1), 1L);

        InOrder order = inOrder(userRepository, cartItemRepository);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(cartItemRepository).findByUserIdAndProductId(1L, 10L);
        then(userRepository).should(never()).getReferenceById(any());
    }

    @Test
    @DisplayName("addItem — usuário inexistente → ResourceNotFoundException")
    void addItem_userNotFound_throws() {
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> cartService.addItem(new AddCartItemRequest(10L, 1), 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        then(cartItemRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("addItem — insufficient stock throws BusinessException")
    void addItem_insufficientStock_throws() {
        product.setStockQuantity(1);
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(new AddCartItemRequest(10L, 5), 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("addItem — product not found throws ResourceNotFoundException")
    void addItem_productNotFound_throws() {
        given(productRepository.findByIdAndActiveTrue(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(new AddCartItemRequest(99L, 1), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateItem — quantity is updated")
    void updateItem_success() {
        CartItem ci = CartItem.builder().id(1L).user(user).product(product).quantity(1).build();
        given(cartItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(ci));
        given(cartItemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        CartItem updated = CartItem.builder().id(1L).user(user).product(product).quantity(3).build();
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(updated));

        CartSummaryResponse summary = cartService.updateItem(1L, new UpdateCartItemRequest(3), 1L);

        assertThat(summary.total()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("removeItem — existing item is deleted")
    void removeItem_success() {
        CartItem ci = CartItem.builder().id(1L).user(user).product(product).quantity(1).build();
        given(cartItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(ci));
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of());

        CartSummaryResponse summary = cartService.removeItem(1L, 1L);

        then(cartItemRepository).should().delete(ci);
        assertThat(summary.itemCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("removeItem — not found throws ResourceNotFoundException")
    void removeItem_notFound_throws() {
        given(cartItemRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- revisão 3, A3: limites anti-açambarcamento ----------

    @Test
    @DisplayName("A3: somar no carrinho além de 10 unidades do mesmo produto é recusado")
    void addItem_unitsPerProductLimit() {
        product.setStockQuantity(100);
        CartItem existing = CartItem.builder().id(1L).user(user).product(product).quantity(8).build();
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addItem(new AddCartItemRequest(10L, 3), 1L))
                .isInstanceOf(BusinessException.class).hasMessage("Maximum of 10 units per product");
        assertThat(existing.getQuantity()).isEqualTo(8);
        then(cartItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("A3: o carrinho aceita no máximo 20 produtos diferentes")
    void addItem_distinctProductsLimit() {
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(cartItemRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.empty());
        given(cartItemRepository.countByUserId(1L)).willReturn((long) CartService.MAX_DISTINCT_ITEMS);

        assertThatThrownBy(() -> cartService.addItem(new AddCartItemRequest(10L, 1), 1L))
                .isInstanceOf(BusinessException.class).hasMessage("Maximum of 20 different products in the cart");
        then(cartItemRepository).should(never()).save(any());
    }
}
