package com.sskings.ecommerce.mail;

import com.sskings.ecommerce.event.OrderEvent;
import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.Payment;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisos de pedido ao cliente por e-mail (ADR-025). Só depois do commit (rollback não avisa) e só para e-mail
 * confirmado de conta não anonimizada — um endereço não confirmado pode ser de outra pessoa.
 */
@Component
@RequiredArgsConstructor
public class CustomerOrderMailer {

    private final OrderRepository orders;
    private final MailOutbox mail;
    private final MailProperties props;

    /** Transação própria (somente leitura) para carregar pedido, itens, cliente e pagamento. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderEvent(OrderEvent event) {
        Order order = orders.findWithItemsById(event.orderId()).orElse(null);
        if (order == null) return;
        User user = order.getUser();
        if (user == null || !user.isEmailVerified() || user.getAnonymizedAt() != null) return;
        MailMessages.Mail m = message(event.type(), order, user.getName());
        if (m != null) mail.send(user.getEmail(), m.subject(), m.body());
    }

    MailMessages.Mail message(OrderEvent.Type type, Order o, String name) {
        Payment p = o.getPayment();
        return switch (type) {
            case CREATED -> p == null ? null : MailMessages.orderCreated(props, name, o.getId(),
                    o.getItems().stream().map(i -> new MailMessages.Item(i.getProductName(), i.getQuantity(), i.getSubtotal())).toList(),
                    o.getTotal(), p.getPixPayload(), p.getExpiresAt());
            case PAID, PAID_MANUALLY -> MailMessages.orderPaid(props, name, o.getId(), o.getTotal());
            case SHIPPED -> MailMessages.orderShipped(props, name, o.getId());
            case DELIVERED -> MailMessages.orderDelivered(props, name, o.getId());
            case CANCELLED_BY_CUSTOMER -> MailMessages.orderCancelled(props, name, o.getId(), "a seu pedido", refundDue(p));
            case CANCELLED_BY_STORE -> MailMessages.orderCancelled(props, name, o.getId(), "pela loja", refundDue(p));
            case PAYMENT_EXPIRED -> MailMessages.orderCancelled(props, name, o.getId(), "o Pix não foi pago no prazo", false);
            case RETURN_REQUESTED -> MailMessages.returnRequested(props, name, o.getId());
            case REFUNDED -> MailMessages.refunded(props, name, o.getId(), o.getTotal());
        };
    }

    private static boolean refundDue(Payment p) {
        return p != null && p.getStatus() == PaymentStatus.REFUND_PENDING;
    }
}
