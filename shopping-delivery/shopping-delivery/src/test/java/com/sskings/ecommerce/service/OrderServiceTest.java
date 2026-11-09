package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.event.OrderEvent;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock AddressRepository addressRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock AuditService auditService;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    OrderService orderService;
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-05T12:00:00Z");
    private static final java.time.LocalDateTime LOCAL_NOW = java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC);

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
        orderService = new OrderService(orderRepository, cartItemRepository, addressRepository, productRepository,
                userRepository, paymentService, new StockService(productRepository), auditService,
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), events);
        lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
    }

    // ---------- revisão 3, A3: uma conta não pode segurar o estoque da loja ----------

    @Test
    @DisplayName("A3: com 2 pedidos aguardando pagamento, o 3º checkout é recusado antes de baixar estoque")
    void checkout_pendingOrdersLimit() {
        given(orderRepository.countByUserIdAndStatus(1L, OrderStatus.PENDING)).willReturn((long) OrderService.MAX_PENDING_ORDERS);

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 orders awaiting payment");
        then(productRepository).should(never()).decrementStock(anyLong(), anyInt());
        then(cartItemRepository).should(never()).findAllByUserId(any());
    }

    // ---------- revisão 4, R4-4: os tetos da revisão 3 não protegiam produto de estoque baixo ----------

    @Test
    @DisplayName("R4-4: as 10 unidades por produto valem somando os pedidos que ainda aguardam pagamento")
    void checkout_unitsAcrossPendingOrders() {
        product.setStockQuantity(100);
        cartItem.setQuantity(3);
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(java.util.Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(java.util.Optional.of(product));
        given(orderRepository.sumPendingUnits(1L, 10L)).willReturn(8L);

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("across your orders awaiting payment");
        then(productRepository).should(never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("R4-4: um pedido leva no máximo metade do que resta do produto (estoque 10 → 5)")
    void checkout_halfOfRemainingStock() {
        product.setStockQuantity(10);
        cartItem.setQuantity(6);
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(java.util.Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(java.util.Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Maximum quantity for this product right now: 5 (Widget)");
        then(productRepository).should(never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("R4-4: a última unidade pode ser comprada (metade de 1 arredonda para 1)")
    void checkout_lastUnit() {
        product.setStockQuantity(1);
        cartItem.setQuantity(1);
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(java.util.Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(java.util.Optional.of(product));
        given(productRepository.decrementStock(10L, 1)).willReturn(1);
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        assertThat(orderService.checkout(new CheckoutRequest(1L), 1L).items()).hasSize(1);
    }

    @Test
    @DisplayName("R4-4: 3 Pix abandonados em 24 h (expirados ou cancelados sem pagar) bloqueiam novos pedidos")
    void checkout_abandonedCooldown() {
        given(orderRepository.countAbandonedPaymentsSince(1L, LOCAL_NOW.minusHours(24)))
                .willReturn((long) OrderService.MAX_ABANDONED_PER_DAY);

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Too many unpaid orders in the last 24 hours. Try again later");
        then(cartItemRepository).should(never()).findAllByUserId(any());
    }

    @Test
    @DisplayName("A3: item de carrinho antigo acima do limite por produto é recusado no checkout")
    void checkout_unitsPerItemLimit() {
        cartItem.setQuantity(CartService.MAX_UNITS_PER_ITEM + 1);
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(java.util.Optional.of(address));

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Maximum of 10 units per product");
        then(productRepository).should(never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("A3: o checkout trava o usuário (dois checkouts simultâneos não furam o limite)")
    void checkout_locksUser() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of());
        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L)).hasMessage("Cart is empty");
        then(userRepository).should().findByIdForUpdate(1L);
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
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(productRepository.decrementStock(10L, 2)).willReturn(0);   // o banco recusou: sem estoque

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .isInstanceOf(BusinessException.class).hasMessage("Insufficient stock for: Widget");
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("checkout — produto desativado depois de ir para o carrinho é recusado")
    void checkout_inactiveProduct_throws() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(new CheckoutRequest(1L), 1L))
                .hasMessage("Product no longer available: Widget");
    }

    @Test
    @DisplayName("checkout — success creates order, deducts stock, clears cart")
    void checkout_success() {
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(cartItem));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        given(productRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(product));
        given(productRepository.decrementStock(10L, 2)).willReturn(1);
        Order savedOrder = Order.builder().id(100L).user(user).address(address)
                .status(OrderStatus.PENDING).total(new BigDecimal("20.00"))
                .items(new ArrayList<>()).build();
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        OrderResponse resp = orderService.checkout(new CheckoutRequest(1L), 1L);

        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.status()).isEqualTo(OrderStatus.PENDING);
        then(cartItemRepository).should().deleteAllByUserId(1L);
        then(paymentService).should().createPixCharge(savedOrder);
        then(productRepository).should().decrementStock(10L, 2);   // baixa atômica no banco
        then(events).should().publishEvent(new OrderEvent(100L, OrderEvent.Type.CREATED));   // aviso do bot (após o commit)
    }

    @Test
    @DisplayName("checkout — baixa o estoque em ordem de id do produto, não na ordem do carrinho (evita deadlock)")
    void checkout_decrementsStockInProductIdOrder() {
        Product a = Product.builder().id(5L).name("A").price(BigDecimal.ONE).stockQuantity(9).build();
        Product c = Product.builder().id(30L).name("C").price(BigDecimal.ONE).stockQuantity(9).build();
        // carrinho na ordem em que o cliente adicionou: 30, 10, 5
        given(cartItemRepository.findAllByUserId(1L)).willReturn(List.of(
                CartItem.builder().id(3L).user(user).product(c).quantity(1).build(),
                cartItem,
                CartItem.builder().id(4L).user(user).product(a).quantity(1).build()));
        given(addressRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(address));
        for (Product p : List.of(a, product, c)) given(productRepository.findByIdAndActiveTrue(p.getId())).willReturn(Optional.of(p));
        given(productRepository.decrementStock(any(), org.mockito.ArgumentMatchers.anyInt())).willReturn(1);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        orderService.checkout(new CheckoutRequest(1L), 1L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(productRepository);
        order.verify(productRepository).decrementStock(5L, 1);
        order.verify(productRepository).decrementStock(10L, 2);
        order.verify(productRepository).decrementStock(30L, 1);
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
                .status(OrderStatus.PENDING).total(BigDecimal.TEN).items(new ArrayList<>()).build();
        order.getItems().add(OrderItem.builder().id(5L).order(order).product(product).productName("Widget")
                .quantity(1).unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN).build());
        given(orderRepository.findAllByUserIdWithItems(1L)).willReturn(List.of(order));

        List<OrderResponse> orders = orderService.findAllByUser(1L);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).id()).isEqualTo(1L);
        // productId permite ao front agrupar o histórico por produto e linkar para a página dele
        assertThat(orders.get(0).items().get(0).productId()).isEqualTo(10L);
    }

    // ---------- cancelamento e arrependimento pelo cliente ----------

    private Order placed(OrderStatus status) {
        Order o = Order.builder().id(7L).user(user).address(address).status(status).total(BigDecimal.TEN)
                .items(new ArrayList<>()).build();
        o.getItems().add(OrderItem.builder().id(1L).order(o).product(product).productName("Widget")
                .quantity(2).unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN).build());
        given(orderRepository.findByIdAndUserIdWithItems(7L, 1L)).willReturn(Optional.of(o));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        return o;
    }

    @Test
    @DisplayName("Cliente cancela pedido ainda não enviado: estoque devolvido e pagamento tratado")
    void cancelByCustomer() {
        Order o = placed(OrderStatus.CONFIRMED);

        OrderResponse resp = orderService.cancelByCustomer(7L, 1L);

        assertThat(resp.status()).isEqualTo(OrderStatus.CANCELLED);
        then(productRepository).should().incrementStock(10L, 2);
        then(paymentService).should().onOrderCancelled(o);
        then(auditService).should().record(eq(user), eq(AuditService.ORDER_CANCELLED_BY_CUSTOMER), eq("ORDER"), eq(7L), any());
        then(events).should().publishEvent(new OrderEvent(7L, OrderEvent.Type.CANCELLED_BY_CUSTOMER));
    }

    @Test
    @DisplayName("Pedido enviado não pode mais ser cancelado pelo cliente; pedido de outro usuário → 404")
    void cancelByCustomer_notAllowed() {
        placed(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orderService.cancelByCustomer(7L, 1L)).hasMessage("Order can no longer be cancelled");
        given(orderRepository.findByIdAndUserIdWithItems(7L, 2L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.cancelByCustomer(7L, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Arrependimento dentro de 7 dias da entrega é registrado (motivo opcional)")
    void requestReturn() {
        Order o = placed(OrderStatus.DELIVERED);
        o.setDeliveredAt(LOCAL_NOW.minusDays(7));

        OrderResponse resp = orderService.requestReturn(7L, 1L, "  não serviu  ");

        assertThat(o.getReturnRequestedAt()).isEqualTo(LOCAL_NOW);
        assertThat(o.getReturnReason()).isEqualTo("não serviu");
        assertThat(resp.returnRequestedAt()).isEqualTo(LOCAL_NOW);
        then(auditService).should().record(user, AuditService.RETURN_REQUESTED, "ORDER", 7L, null);
        then(events).should().publishEvent(new OrderEvent(7L, OrderEvent.Type.RETURN_REQUESTED));
    }

    @Test
    @DisplayName("Arrependimento sem motivo, fora do prazo, repetido ou antes da entrega é recusado")
    void requestReturn_rules() {
        Order o = placed(OrderStatus.DELIVERED);
        o.setDeliveredAt(LOCAL_NOW.minusDays(2));
        orderService.requestReturn(7L, 1L, " ");
        assertThat(o.getReturnReason()).isNull();
        assertThatThrownBy(() -> orderService.requestReturn(7L, 1L, null)).hasMessageContaining("already requested");

        o.setReturnRequestedAt(null);
        o.setDeliveredAt(LOCAL_NOW.minusDays(7).minusSeconds(1));
        assertThatThrownBy(() -> orderService.requestReturn(7L, 1L, null)).hasMessageContaining("expired");

        o.setStatus(OrderStatus.SHIPPED);
        o.setDeliveredAt(null);
        assertThatThrownBy(() -> orderService.requestReturn(7L, 1L, null)).isInstanceOf(BusinessException.class);
    }
}
