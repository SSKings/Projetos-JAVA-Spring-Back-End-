package com.sskings.ecommerce.mail;

import com.sskings.ecommerce.event.OrderEvent;
import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.OrderItem;
import com.sskings.ecommerce.model.Payment;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** ADR-025: avisos de pedido ao cliente. */
class CustomerOrderMailerTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final MailOutbox outbox = mock(MailOutbox.class);
    private final MailProperties props = new MailProperties(true, "l@gmail.com", "https://loja.com.br", false, null);
    private final CustomerOrderMailer mailer = new CustomerOrderMailer(orders, outbox, props);
    private final User user = User.builder().id(1L).name("Ana Souza").email("ana@teste.com")
            .emailVerifiedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    private final Order order = Order.builder().id(12L).user(user).total(new BigDecimal("59.80"))
            .items(List.of(OrderItem.builder().productName("Caneca").quantity(2).subtotal(new BigDecimal("59.80")).build()))
            .payment(Payment.builder().status(PaymentStatus.PENDING).pixPayload("00020126PIX")
                    .expiresAt(LocalDateTime.of(2026, 11, 10, 15, 30)).build())
            .build();

    @Test
    @DisplayName("Cada evento vira o e-mail certo; ações sem aviso ao cliente não mandam nada")
    void eachEvent() {
        given(orders.findWithItemsById(12L)).willReturn(Optional.of(order));
        for (OrderEvent.Type type : OrderEvent.Type.values()) mailer.onOrderEvent(new OrderEvent(12L, type));
        then(outbox).should(times(OrderEvent.Type.values().length)).send(eq("ana@teste.com"), anyString(), anyString());
        then(outbox).should().send(eq("ana@teste.com"), startsWith("Pedido #12 recebido"), contains("00020126PIX"));
        then(outbox).should(times(2)).send(eq("ana@teste.com"), eq("Pagamento confirmado — pedido #12"), anyString());
        then(outbox).should().send(eq("ana@teste.com"), eq("Pedido #12 enviado"), anyString());

        order.getPayment().setStatus(PaymentStatus.REFUND_PENDING);
        assertThat(mailer.message(OrderEvent.Type.CANCELLED_BY_STORE, order, "Ana").body()).contains("devolver o valor");
        order.setPayment(null);
        assertThat(mailer.message(OrderEvent.Type.CREATED, order, "Ana")).isNull();          // sem cobrança, sem e-mail
    }

    @Test
    @DisplayName("E-mail não confirmado, conta anonimizada ou pedido inexistente: nada é enviado")
    void onlyVerifiedOwners() {
        given(orders.findWithItemsById(12L)).willReturn(Optional.of(order));
        user.setEmailVerifiedAt(null);
        mailer.onOrderEvent(new OrderEvent(12L, OrderEvent.Type.SHIPPED));
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setAnonymizedAt(LocalDateTime.now());
        mailer.onOrderEvent(new OrderEvent(12L, OrderEvent.Type.SHIPPED));
        given(orders.findWithItemsById(99L)).willReturn(Optional.empty());
        mailer.onOrderEvent(new OrderEvent(99L, OrderEvent.Type.SHIPPED));
        then(outbox).shouldHaveNoInteractions();
    }
}
