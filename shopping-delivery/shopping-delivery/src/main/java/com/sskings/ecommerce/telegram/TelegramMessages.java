package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.dto.response.DashboardResponse;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.dto.response.PaymentResponse;
import com.sskings.ecommerce.dto.response.UserResponse;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.telegram.TelegramClient.Button;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Textos do bot (HTML do Telegram). Tudo que vem do banco ou do cliente passa por {@link #esc}: nome de produto,
 * nome do cliente e motivo da devolução são texto livre e não podem injetar tags/links na mensagem do admin.
 * Dados pessoais vão no mínimo necessário: o endereço aparece só como cidade/UF (o completo fica no painel).
 */
final class TelegramMessages {

    static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private TelegramMessages() {}

    static final String HELP = """
            <b>ShopEasy — bot de administração</b>

            <b>Pedidos</b>
            /pedidos — últimos %1$d pedidos
            /pedidos pendentes — por status (pendentes, confirmados, enviados, entregues, cancelados)
            /pedido 12 — detalhes e ações do pedido #12

            <b>Usuários</b>
            /usuarios — últimos %1$d cadastros
            /usuarios ana — busca por nome ou e-mail
            /usuario 5 — detalhes e ações do usuário #5

            /resumo — números da loja
            /ajuda — esta mensagem

            Toda ação pede confirmação e fica na auditoria com o seu usuário. \
            Novos pedidos, Pix confirmados, cancelamentos e devoluções chegam aqui automaticamente.""".formatted(PAGE_SIZE);

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String money(BigDecimal value) {
        return value == null ? "-" : NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }

    static String date(LocalDateTime d) {
        return d == null ? "-" : DATE.format(d);
    }

    static String status(OrderStatus s) {
        return switch (s) {
            case PENDING -> "⏳ Pendente";
            case CONFIRMED -> "✅ Confirmado";
            case SHIPPED -> "🚚 Enviado";
            case DELIVERED -> "📦 Entregue";
            case CANCELLED -> "❌ Cancelado";
        };
    }

    /** Verbo do botão que leva o pedido a este status. */
    static String statusAction(OrderStatus s) {
        return switch (s) {
            case PENDING -> "Voltar a pendente";
            case CONFIRMED -> "✅ Confirmar";
            case SHIPPED -> "🚚 Marcar enviado";
            case DELIVERED -> "📦 Marcar entregue";
            case CANCELLED -> "❌ Cancelar";
        };
    }

    static String payment(PaymentStatus s) {
        return switch (s) {
            case PENDING -> "aguardando Pix";
            case PAID -> "pago";
            case EXPIRED -> "Pix expirado";
            case CANCELLED -> "cancelado";
            case REFUND_PENDING -> "⚠️ estorno pendente";
            case REFUNDED -> "estornado";
        };
    }

    static String role(Role r) {
        return r == Role.ADMIN ? "admin" : "cliente";
    }

    static String dashboard(DashboardResponse d) {
        return """
                <b>📊 Resumo da loja</b>
                Pedidos: %d (%d pendentes)
                Faturamento: %s
                Clientes: %d
                Produtos: %d ativos de %d · %d com estoque baixo""".formatted(
                d.totalOrders(), d.pendingOrders(), money(d.revenue()), d.totalCustomers(),
                d.activeProducts(), d.totalProducts(), d.lowStockProducts());
    }

    static String orderLine(AdminOrderResponse o) {
        return "/pedido_%d · %s · %s · %s".formatted(o.id(), status(o.status()), money(o.total()), esc(o.customerName()));
    }

    static String orderLine(OrderResponse o) {
        return "/pedido_%d · %s · %s · %s".formatted(o.id(), status(o.status()), money(o.total()), date(o.createdAt()));
    }

    static String userLine(UserResponse u) {
        return "/usuario_%d · %s · %s%s".formatted(u.id(), esc(u.name()), esc(u.email()),
                (u.active() ? "" : " · 🚫 bloqueado") + (u.role() == Role.ADMIN ? " · ⭐ admin" : ""));
    }

    static String order(AdminOrderResponse o) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Pedido #").append(o.id()).append("</b> · ").append(status(o.status())).append('\n');
        sb.append("Cliente: ").append(esc(o.customerName())).append(" (").append(esc(o.customerEmail()))
                .append(") /usuario_").append(o.customerId()).append('\n');
        sb.append("Total: <b>").append(money(o.total())).append("</b> · criado em ").append(date(o.createdAt())).append('\n');
        PaymentResponse p = o.payment();
        if (p != null) {
            sb.append("Pagamento: Pix ").append(payment(p.status()));
            if (p.status() == PaymentStatus.PENDING) sb.append(" (vence ").append(date(p.expiresAt())).append(')');
            if (p.paidAt() != null) sb.append(" em ").append(date(p.paidAt()));
            sb.append('\n');
        }
        if (o.address() != null) {
            sb.append("Entrega: ").append(esc(o.address().city())).append('/').append(esc(o.address().state())).append('\n');
        }
        if (o.deliveredAt() != null) sb.append("Entregue em ").append(date(o.deliveredAt())).append('\n');
        if (o.returnRequestedAt() != null) {
            sb.append("↩️ Devolução pedida em ").append(date(o.returnRequestedAt()));
            if (o.returnReason() != null) sb.append(": <i>").append(esc(o.returnReason())).append("</i>");
            sb.append('\n');
        }
        sb.append("\n<b>Itens</b>\n");
        o.items().forEach(i -> sb.append("• ").append(i.quantity()).append("× ").append(esc(i.productName()))
                .append(" — ").append(money(i.subtotal())).append('\n'));
        return sb.toString().stripTrailing();
    }

    /** Ações possíveis agora: as mesmas regras do painel (status seguintes e situação do Pix vêm do back-end). */
    static List<List<Button>> orderButtons(AdminOrderResponse o) {
        List<List<Button>> rows = new ArrayList<>();
        List<Button> statusRow = o.nextStatuses().stream()
                .map(s -> new Button(statusAction(s), "?st:" + o.id() + ":" + s)).toList();
        if (!statusRow.isEmpty()) rows.add(statusRow);
        PaymentResponse p = o.payment();
        if (p != null && p.status() == PaymentStatus.PENDING) {
            rows.add(List.of(new Button("💰 Confirmar Pix recebido", "?pay:" + o.id())));
        }
        if (p != null && (p.status() == PaymentStatus.REFUND_PENDING
                || (p.status() == PaymentStatus.PAID && o.returnRequestedAt() != null))) {
            rows.add(List.of(new Button("↩️ Registrar estorno do Pix", "?ref:" + o.id())));
        }
        rows.add(List.of(new Button("🔄 Atualizar", "o:" + o.id()), new Button("👤 Cliente", "u:" + o.customerId())));
        return rows;
    }

    static String user(UserResponse u) {
        return """
                <b>Usuário #%d</b> · %s
                %s
                E-mail: %s
                Telefone: %s
                Papel: %s · %s
                Pedidos: %d · desde %s""".formatted(u.id(), esc(u.name()), u.active() ? "✅ ativo" : "🚫 bloqueado",
                esc(u.email()), u.phone() == null ? "-" : esc(u.phone()), role(u.role()),
                u.role() == Role.ADMIN ? "acessa o painel" : "compra na loja", u.orderCount(), date(u.createdAt()));
    }

    static List<List<Button>> userButtons(UserResponse u, Long currentAdminId) {
        List<List<Button>> rows = new ArrayList<>();
        if (!u.id().equals(currentAdminId)) {   // a API recusa alterar a própria conta: nem mostra o botão
            rows.add(List.of(
                    u.active() ? new Button("🚫 Bloquear", "?act:" + u.id() + ":false")
                               : new Button("✅ Desbloquear", "?act:" + u.id() + ":true"),
                    u.role() == Role.ADMIN ? new Button("👤 Tornar cliente", "?role:" + u.id() + ":CUSTOMER")
                                           : new Button("⭐ Tornar admin", "?role:" + u.id() + ":ADMIN")));
        }
        rows.add(List.of(new Button("📦 Pedidos", "uo:" + u.id()), new Button("🔄 Atualizar", "u:" + u.id())));
        return rows;
    }

    /** Pergunta de confirmação para a ação de um botão "?…". null = ação desconhecida. */
    static String confirmation(String action) {
        String[] a = action.split(":");
        return switch (a[0]) {
            case "st" -> "Mudar o pedido #%s para <b>%s</b>?".formatted(a[1], status(OrderStatus.valueOf(a[2])))
                    + (OrderStatus.valueOf(a[2]) == OrderStatus.CANCELLED ? "\nO estoque volta e, se o Pix já foi pago, fica estorno pendente." : "");
            case "pay" -> "Confirmar que o Pix do pedido #%s caiu na conta? Confira o extrato antes.".formatted(a[1]);
            case "ref" -> "Registrar que o Pix do pedido #%s foi devolvido ao cliente?".formatted(a[1]);
            case "act" -> Boolean.parseBoolean(a[2])
                    ? "Desbloquear o usuário #%s?".formatted(a[1])
                    : "Bloquear o usuário #%s? As sessões abertas dele caem na hora.".formatted(a[1]);
            case "role" -> Role.valueOf(a[2]) == Role.ADMIN
                    ? "Tornar o usuário #%s <b>admin</b>? Ele passa a acessar o painel (com MFA).".formatted(a[1])
                    : "Tirar o acesso de admin do usuário #%s?".formatted(a[1]);
            default -> null;
        };
    }
}
