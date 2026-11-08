package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.event.OrderEvent;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.telegram.TelegramClient.Button;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.sskings.ecommerce.telegram.TelegramMessages.*;

/**
 * Avisa os admins no Telegram sobre os pedidos. Escuta {@link OrderEvent} DEPOIS do commit (rollback não avisa) e
 * envia numa thread própria: o checkout do cliente não espera pela rede do Telegram nem falha se ela cair.
 * Só recebe quem ainda é admin ativo (com MFA, se exigido): admin bloqueado deixa de ver dados de clientes.
 */
@Slf4j
public class TelegramOrderNotifier {

    private final TelegramClient client;
    private final TelegramBotProperties props;
    private final TelegramAdminBot bot;
    private final UserRepository userRepository;
    private final AdminOrderService orders;
    private final Executor executor;

    public TelegramOrderNotifier(TelegramClient client, TelegramBotProperties props, TelegramAdminBot bot,
                                 UserRepository userRepository, AdminOrderService orders, Executor executor) {
        this.client = client;
        this.props = props;
        this.bot = bot;
        this.userRepository = userRepository;
        this.orders = orders;
        this.executor = executor;
    }

    @TransactionalEventListener
    public void onOrderEvent(OrderEvent event) {
        executor.execute(() -> notifyAdmins(event));
    }

    void notifyAdmins(OrderEvent event) {
        try {
            AdminOrderResponse o = orders.findById(event.orderId());
            String text = text(event.type(), o);
            List<List<Button>> buttons = orderButtons(o);
            for (Map.Entry<Long, String> chat : props.adminChats().entrySet()) {
                boolean allowed = userRepository.findByEmail(chat.getValue()).filter(bot::isAllowed).isPresent();
                if (!allowed) continue;
                try {
                    client.sendMessage(chat.getKey(), text, buttons);
                } catch (RuntimeException e) {
                    log.warn("Telegram: aviso do pedido {} não entregue ao chat {}: {}", o.id(), chat.getKey(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Telegram: falha ao montar o aviso {} do pedido {}: {}", event.type(), event.orderId(), e.getMessage());
        }
    }

    static String text(OrderEvent.Type type, AdminOrderResponse o) {
        String header = switch (type) {
            case CREATED -> "🛒 <b>Novo pedido #%d</b> · %s (aguardando Pix)".formatted(o.id(), money(o.total()));
            case PAID -> "💰 <b>Pix confirmado — pedido #%d</b> · %s. Pronto para separar e enviar.".formatted(o.id(), money(o.total()));
            case CANCELLED_BY_CUSTOMER -> "❌ <b>Pedido #%d cancelado pelo cliente</b>".formatted(o.id())
                    + (o.payment() != null && o.payment().status() == com.sskings.ecommerce.model.enums.PaymentStatus.REFUND_PENDING
                       ? "\n⚠️ O Pix já tinha sido pago: devolva o valor e registre o estorno." : "");
            case RETURN_REQUESTED -> "↩️ <b>Devolução solicitada — pedido #%d</b> (arrependimento, CDC art. 49)".formatted(o.id());
            case PAYMENT_EXPIRED -> "⌛ <b>Pix do pedido #%d expirou</b>: pedido cancelado e estoque devolvido.".formatted(o.id());
        };
        return header + "\n\n" + order(o);
    }
}
