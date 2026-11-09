package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.dto.response.UserResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.SecurityProperties;
import com.sskings.ecommerce.service.admin.AdminDashboardService;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.service.admin.AdminUserService;
import com.sskings.ecommerce.telegram.TelegramClient.Button;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.sskings.ecommerce.telegram.TelegramMessages.*;

/**
 * Trata cada mensagem/clique recebido pelo bot.
 *
 * Segurança (ADR-024):
 * - só chats privados listados em TELEGRAM_ADMIN_BOT_ADMINS; o resto é ignorado (só /start e /id respondem o chat ID,
 *   para a configuração);
 * - a cada mensagem, a conta ligada ao chat precisa continuar ADMIN, ativa e (se exigido) com MFA: bloquear o admin
 *   no painel corta o bot na hora;
 * - admin com MFA precisa abrir a sessão com /entrar + código do app (TelegramSessions, revisão 3 M2): ter o Telegram
 *   aberto não basta, como no painel;
 * - as ações chamam os MESMOS serviços do painel (mesmas regras de status, estoque, Pix e "não alterar a si mesmo"),
 *   com o admin no SecurityContext: a auditoria registra quem fez;
 * - toda ação que muda dados é um botão "?…" que pede confirmação; só o "!…" executa.
 */
@Slf4j
public class TelegramAdminBot {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Map<String, OrderStatus> STATUS_ALIASES = Map.of(
            "pendentes", OrderStatus.PENDING, "confirmados", OrderStatus.CONFIRMED, "enviados", OrderStatus.SHIPPED,
            "entregues", OrderStatus.DELIVERED, "cancelados", OrderStatus.CANCELLED);

    private final TelegramClient client;
    private final TelegramBotProperties props;
    private final SecurityProperties security;
    private final UserRepository userRepository;
    private final AdminOrderService orders;
    private final AdminUserService users;
    private final AdminDashboardService dashboard;
    private final TelegramSessions sessions;

    public TelegramAdminBot(TelegramClient client, TelegramBotProperties props, SecurityProperties security,
                            UserRepository userRepository, AdminOrderService orders, AdminUserService users,
                            AdminDashboardService dashboard, TelegramSessions sessions) {
        this.client = client;
        this.props = props;
        this.security = security;
        this.userRepository = userRepository;
        this.orders = orders;
        this.users = users;
        this.dashboard = dashboard;
        this.sessions = sessions;
    }

    public void handle(JsonNode update) {
        JsonNode callback = update.path("callback_query");
        JsonNode message = callback.isMissingNode() ? update.path("message") : callback.path("message");
        long chatId = message.path("chat").path("id").asLong();
        long fromId = (callback.isMissingNode() ? message : callback).path("from").path("id").asLong();
        String text = callback.isMissingNode() ? message.path("text").asText("").trim() : null;
        if (chatId == 0) return;

        if (!callback.isMissingNode()) {
            client.answerCallback(callback.path("id").asText(), null);
        }
        // Só conversa privada: num grupo, chatId != fromId e qualquer membro poderia clicar nos botões.
        String email = chatId == fromId ? props.adminChats().get(chatId) : null;
        if (email == null) {
            log.warn("Telegram: mensagem ignorada do chat {} (não autorizado)", chatId);
            if (text != null && (text.startsWith("/start") || text.startsWith("/id"))) {
                client.sendMessage(chatId, ("Este chat não está autorizado. Seu chat ID é <code>%d</code>: peça para "
                        + "incluí-lo em TELEGRAM_ADMIN_BOT_ADMINS junto com o e-mail da sua conta de admin.").formatted(chatId), null);
            }
            return;
        }
        Optional<User> admin = userRepository.findByEmail(email).filter(this::isAllowed);
        if (admin.isEmpty()) {
            log.warn("Telegram: chat {} recusado — a conta ligada a ele não é um admin ativo{}", chatId,
                    security.requireAdminMfa() ? " com MFA" : "");
            client.sendMessage(chatId, "🔒 Acesso negado: a conta ligada a este chat não é um admin ativo"
                    + (security.requireAdminMfa() ? " com MFA ativado." : "."), null);
            return;
        }

        if (text != null && isCommand(text, "/entrar")) {
            login(chatId, admin.get(), text);
            return;
        }
        if (text != null && isCommand(text, "/sair")) {
            sessions.close(chatId);
            client.sendMessage(chatId, "🔒 Sessão encerrada. Para voltar, envie /entrar e o código do app autenticador.", null);
            return;
        }
        if (!sessions.isOpen(chatId, admin.get())) {
            client.sendMessage(chatId, LOGIN_REQUIRED, null);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(admin.get(), null, admin.get().getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            if (callback.isMissingNode()) command(chatId, text, admin.get());
            else button(chatId, callback.path("data").asText(""), admin.get());
        } catch (BusinessException e) {
            client.sendMessage(chatId, "Password confirmation required".equals(e.getMessage()) ? PANEL_ONLY
                    : "⚠️ " + esc(e.getMessage()), null);
        } catch (ResourceNotFoundException e) {
            client.sendMessage(chatId, "⚠️ " + esc(e.getMessage()), null);
        } catch (IllegalArgumentException e) {
            client.sendMessage(chatId, "⚠️ Comando inválido. Veja /ajuda.", null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isCommand(String text, String cmd) {
        String first = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        return first.equals(cmd);
    }

    private void login(long chatId, User admin, String text) {
        String[] parts = text.split("\\s+", 2);
        String code = parts.length > 1 ? parts[1].trim() : "";
        try {
            if (sessions.open(chatId, admin, code)) {
                client.sendMessage(chatId, "🔓 Sessão aberta por 12 horas. /sair encerra antes.\n\n" + HELP, null);
            } else {
                client.sendMessage(chatId, "⚠️ Código inválido. Envie /entrar seguido do código atual do app autenticador.", null);
            }
        } catch (TooManyRequestsException e) {
            client.sendMessage(chatId, "⛔ Muitas tentativas erradas. Tente de novo em %d min."
                    .formatted((e.getRetryAfterSeconds() + 59) / 60), null);
        }
    }

    /** Mesma regra do painel: ADMIN, ativo, não anonimizado e com MFA quando REQUIRE_ADMIN_MFA=true. */
    boolean isAllowed(User u) {
        return u.getRole() == Role.ADMIN && u.isActive() && u.getAnonymizedAt() == null
                && (!security.requireAdminMfa() || u.isMfaEnabled());
    }

    private void command(long chatId, String text, User admin) {
        // "/pedido_12" (link clicável nas listas) vale o mesmo que "/pedido 12"; "/cmd@NomeDoBot" também
        String[] parts = text.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        String arg = parts.length > 1 ? parts[1].trim() : "";
        int underscore = cmd.indexOf('_');
        if (underscore > 0) {
            arg = cmd.substring(underscore + 1);
            cmd = cmd.substring(0, underscore);
        }
        switch (cmd) {
            case "/start", "/ajuda", "/help" -> client.sendMessage(chatId, HELP, null);
            case "/resumo" -> client.sendMessage(chatId, dashboard(dashboard.getStats()), null);
            case "/pedidos" -> listOrders(chatId, arg);
            case "/pedido" -> showOrder(chatId, id(arg));
            case "/usuarios" -> listUsers(chatId, arg);
            case "/usuario" -> showUser(chatId, id(arg), admin);
            default -> client.sendMessage(chatId, "Não conheço esse comando. Veja /ajuda.", null);
        }
    }

    private void button(long chatId, String data, User admin) {
        if (data.equals("x")) {
            client.sendMessage(chatId, "Ok, nada foi alterado.", null);
            return;
        }
        if (data.startsWith("?")) {
            String action = data.substring(1);
            String question = confirmation(action);
            if (question == null) throw new IllegalArgumentException(data);
            client.sendMessage(chatId, question, List.of(List.of(
                    new Button("Sim, confirmar", "!" + action), new Button("Não", "x"))));
            return;
        }
        boolean execute = data.startsWith("!");
        String[] a = (execute ? data.substring(1) : data).split(":");
        Long id = id(a.length > 1 ? a[1] : "");
        switch ((execute ? "!" : "") + a[0]) {
            case "o" -> showOrder(chatId, id);
            case "u" -> showUser(chatId, id, admin);
            case "uo" -> userOrders(chatId, id);
            case "!st" -> {
                AdminOrderResponse o = orders.updateStatus(id, OrderStatus.valueOf(a[2]));
                done(chatId, "Pedido #%d agora está %s.".formatted(id, status(o.status())), o);
            }
            case "!pay" -> done(chatId, "Pix do pedido #%d confirmado.".formatted(id), orders.confirmPayment(id));
            case "!ref" -> done(chatId, "Estorno do pedido #%d registrado.".formatted(id), orders.refundPayment(id));
            case "!act" -> {
                UserResponse u = users.update(id, new UpdateUserRequest(Boolean.parseBoolean(a[2]), null), admin.getId());
                client.sendMessage(chatId, "✔ Usuário #%d %s.\n\n".formatted(id, u.active() ? "desbloqueado" : "bloqueado")
                        + user(u), userButtons(u, admin.getId()));
            }
            // Mudar papel exige senha e código de quem age (revisão 4, R4-3): só pelo painel
            case "!role" -> client.sendMessage(chatId, PANEL_ONLY, null);
            default -> throw new IllegalArgumentException(data);
        }
    }

    private void done(long chatId, String summary, AdminOrderResponse o) {
        client.sendMessage(chatId, "✔ " + summary + "\n\n" + order(o), orderButtons(o));
    }

    private void listOrders(long chatId, String arg) {
        OrderStatus status = null;
        if (!arg.isEmpty()) {
            String key = arg.toLowerCase(Locale.ROOT);
            status = STATUS_ALIASES.getOrDefault(key, null);
            if (status == null) {
                try {
                    status = OrderStatus.valueOf(arg.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    client.sendMessage(chatId, "Status desconhecido. Use: pendentes, confirmados, enviados, entregues ou cancelados.", null);
                    return;
                }
            }
        }
        List<AdminOrderResponse> page = orders.findAll(status, PageRequest.of(0, PAGE_SIZE, NEWEST)).getContent();
        String title = status == null ? "Últimos pedidos" : "Pedidos " + status(status);
        client.sendMessage(chatId, page.isEmpty() ? "Nenhum pedido encontrado."
                : "<b>" + title + "</b>\n" + String.join("\n", page.stream().map(TelegramMessages::orderLine).toList()), null);
    }

    private void showOrder(long chatId, Long id) {
        AdminOrderResponse o = orders.findById(id);
        client.sendMessage(chatId, order(o), orderButtons(o));
    }

    private void listUsers(long chatId, String search) {
        List<UserResponse> page = users.findAll(search.isEmpty() ? null : search, PageRequest.of(0, PAGE_SIZE, NEWEST)).getContent();
        client.sendMessage(chatId, page.isEmpty() ? "Nenhum usuário encontrado."
                : "<b>" + (search.isEmpty() ? "Últimos cadastros" : "Usuários com \"" + esc(search) + "\"") + "</b>\n"
                  + String.join("\n", page.stream().map(TelegramMessages::userLine).toList()), null);
    }

    private void showUser(long chatId, Long id, User admin) {
        UserResponse u = users.findById(id);
        client.sendMessage(chatId, user(u), userButtons(u, admin.getId()));
    }

    private void userOrders(long chatId, Long id) {
        List<OrderResponse> list = users.findOrders(id);
        client.sendMessage(chatId, list.isEmpty() ? "O usuário #%d não tem pedidos.".formatted(id)
                : "<b>Pedidos do usuário #%d</b>\n".formatted(id)
                  + String.join("\n", list.stream().limit(PAGE_SIZE).map(TelegramMessages::orderLine).toList()), null);
    }

    private static Long id(String s) {
        try {
            long id = Long.parseLong(s.trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid id");
        }
    }
}
