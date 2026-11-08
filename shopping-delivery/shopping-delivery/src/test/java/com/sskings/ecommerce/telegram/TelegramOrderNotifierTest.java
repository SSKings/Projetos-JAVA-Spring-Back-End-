package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.event.OrderEvent;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.sskings.ecommerce.telegram.TelegramFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

class TelegramOrderNotifierTest {

    private final TelegramClient client = mock(TelegramClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminOrderService orders = mock(AdminOrderService.class);
    private final TelegramBotProperties props = new TelegramBotProperties(true, TOKEN,
            "111:admin@shopeasy.com,222:ex-admin@shopeasy.com", null, null);
    private final TelegramAdminBot bot = new TelegramAdminBot(client, props, security(true), userRepository, orders, null, null);
    private final TelegramOrderNotifier notifier = new TelegramOrderNotifier(client, props, bot, userRepository, orders, Runnable::run);

    @BeforeEach
    void setUp() {
        given(userRepository.findByEmail("admin@shopeasy.com")).willReturn(Optional.of(admin()));
        given(userRepository.findByEmail("ex-admin@shopeasy.com")).willReturn(Optional.of(
                User.builder().email("ex-admin@shopeasy.com").role(Role.CUSTOMER).active(true).build()));
    }

    @Test
    @DisplayName("Novo pedido: aviso com o pedido e botões só para quem ainda é admin")
    void newOrder() {
        given(orders.findById(12L)).willReturn(order(OrderStatus.PENDING, payment(PaymentStatus.PENDING), null));

        notifier.onOrderEvent(new OrderEvent(12L, OrderEvent.Type.CREATED));

        verify(client).sendMessage(eq(111L), argThat(t -> t.startsWith("🛒 <b>Novo pedido #12</b> · R$ 59,90") && t.contains("Itens")),
                argThat(kb -> kb.stream().flatMap(java.util.List::stream).anyMatch(b -> b.data().equals("?pay:12"))));
        verify(client, never()).sendMessage(eq(222L), anyString(), any());
    }

    @Test
    @DisplayName("Texto de cada tipo de aviso")
    void texts() {
        var paid = order(OrderStatus.CONFIRMED, payment(PaymentStatus.PAID), null);
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.PAID, paid)).contains("Pix confirmado", "Pronto para separar");
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.CANCELLED_BY_CUSTOMER, order(OrderStatus.CANCELLED, payment(PaymentStatus.REFUND_PENDING), null)))
                .contains("cancelado pelo cliente", "registre o estorno");
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.CANCELLED_BY_CUSTOMER, order(OrderStatus.CANCELLED, payment(PaymentStatus.CANCELLED), null)))
                .doesNotContain("estorno");
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.CANCELLED_BY_CUSTOMER, order(OrderStatus.CANCELLED, null, null)))
                .doesNotContain("estorno");
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.RETURN_REQUESTED, order(OrderStatus.DELIVERED, payment(PaymentStatus.PAID), NOW)))
                .contains("Devolução solicitada", "CDC");
        assertThat(TelegramOrderNotifier.text(OrderEvent.Type.PAYMENT_EXPIRED, order(OrderStatus.CANCELLED, payment(PaymentStatus.EXPIRED), null)))
                .contains("expirou", "estoque devolvido");
    }

    @Test
    @DisplayName("Falha de envio para um chat não impede os outros; falha ao carregar o pedido só gera log")
    void failuresAreContained() {
        TelegramBotProperties two = new TelegramBotProperties(true, TOKEN, "111:admin@shopeasy.com,333:admin@shopeasy.com", null, null);
        var n = new TelegramOrderNotifier(client, two, bot, userRepository, orders, Runnable::run);
        given(orders.findById(12L)).willReturn(order(OrderStatus.PENDING, payment(PaymentStatus.PENDING), null));
        willThrow(new TelegramException("sendMessage failed: blocked")).given(client).sendMessage(eq(111L), anyString(), any());

        n.onOrderEvent(new OrderEvent(12L, OrderEvent.Type.CREATED));
        verify(client).sendMessage(eq(333L), anyString(), any());

        given(orders.findById(13L)).willThrow(new IllegalStateException("db down"));
        n.onOrderEvent(new OrderEvent(13L, OrderEvent.Type.PAID));   // não lança
    }
}
