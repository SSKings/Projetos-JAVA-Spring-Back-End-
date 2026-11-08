package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.dto.response.DashboardResponse;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.service.admin.AdminDashboardService;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.service.admin.AdminUserService;
import com.sskings.ecommerce.telegram.TelegramClient.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.sskings.ecommerce.telegram.TelegramFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramAdminBotTest {

    @Mock TelegramClient client;
    @Mock UserRepository userRepository;
    @Mock AdminOrderService orders;
    @Mock AdminUserService users;
    @Mock AdminDashboardService dashboard;

    private TelegramAdminBot bot;
    private final User admin = admin();

    @BeforeEach
    void setUp() {
        bot = new TelegramAdminBot(client, props(), security(true), userRepository, orders, users, dashboard);
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Texto (e botões) da última mensagem enviada ao chat do admin. */
    private String lastText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).sendMessage(eq(ADMIN_CHAT), text.capture(), any());
        return text.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<List<Button>> lastButtons() {
        ArgumentCaptor<List<List<Button>>> kb = ArgumentCaptor.forClass(List.class);
        verify(client, atLeastOnce()).sendMessage(eq(ADMIN_CHAT), anyString(), kb.capture());
        return kb.getValue();
    }

    private static List<String> data(List<List<Button>> keyboard) {
        return keyboard.stream().flatMap(List::stream).map(Button::data).toList();
    }

    // ---------- autorização ----------

    @Test
    @DisplayName("Chat fora da lista: ignorado; /start e /id respondem só o chat ID para a configuração")
    void unknownChat() {
        bot.handle(message(999, "/pedidos"));
        verifyNoInteractions(client, orders, users, userRepository);

        bot.handle(message(999, "/start"));
        verify(client).sendMessage(eq(999L), contains("<code>999</code>"), isNull());
        verifyNoInteractions(orders, users, userRepository);
    }

    @Test
    @DisplayName("Grupo (chat diferente de quem enviou) nunca é atendido, mesmo com o ID de um admin")
    void groupIgnored() {
        var update = message(ADMIN_CHAT, "/pedidos");
        ((com.fasterxml.jackson.databind.node.ObjectNode) update.get("message").get("from")).put("id", 777);
        bot.handle(update);
        verifyNoInteractions(orders, userRepository);
    }

    @Test
    @DisplayName("Conta ligada ao chat que deixou de ser admin, foi bloqueada, anonimizada ou está sem MFA: acesso negado")
    void adminNoLongerAllowed() {
        for (User u : List.of(
                User.builder().email(ADMIN_EMAIL).role(Role.CUSTOMER).active(true).mfaEnabled(true).build(),
                User.builder().email(ADMIN_EMAIL).role(Role.ADMIN).active(false).mfaEnabled(true).build(),
                User.builder().email(ADMIN_EMAIL).role(Role.ADMIN).active(true).mfaEnabled(true).anonymizedAt(NOW).build(),
                User.builder().email(ADMIN_EMAIL).role(Role.ADMIN).active(true).mfaEnabled(false).build())) {
            given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.of(u));
            bot.handle(message(ADMIN_CHAT, "/pedidos"));
        }
        given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.empty());
        bot.handle(message(ADMIN_CHAT, "/pedidos"));

        verify(client, times(5)).sendMessage(eq(ADMIN_CHAT), contains("Acesso negado"), isNull());
        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("Sem REQUIRE_ADMIN_MFA (dev), admin sem MFA pode usar o bot")
    void mfaNotRequired() {
        bot = new TelegramAdminBot(client, props(), security(false), userRepository, orders, users, dashboard);
        admin.setMfaEnabled(false);
        given(dashboard.getStats()).willReturn(new DashboardResponse(10, 8, 1, 4, 20, 3, new BigDecimal("1234.50")));

        bot.handle(message(ADMIN_CHAT, "/resumo"));

        assertThat(lastText()).contains("Pedidos: 20 (3 pendentes)", "R$ 1.234,50", "8 ativos de 10", "1 com estoque baixo");
    }

    @Test
    @DisplayName("Durante o comando o admin está no SecurityContext (auditoria com o ator); depois o contexto é limpo")
    void securityContext() {
        willAnswer(inv -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(admin);
            return new DashboardResponse(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
        }).given(dashboard).getStats();

        bot.handle(message(ADMIN_CHAT, "/resumo"));

        verify(dashboard).getStats();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ---------- comandos ----------

    @Test
    @DisplayName("/start, /ajuda e /help mostram a ajuda; comando desconhecido aponta para /ajuda")
    void help() {
        bot.handle(message(ADMIN_CHAT, "/start"));
        bot.handle(message(ADMIN_CHAT, "/ajuda@ShopEasyAdminBot"));
        verify(client, times(2)).sendMessage(eq(ADMIN_CHAT), contains("bot de administração"), isNull());

        bot.handle(message(ADMIN_CHAT, "oi"));
        assertThat(lastText()).contains("Não conheço");
    }

    @Test
    @DisplayName("/pedidos lista os mais novos; filtra por status em português ou pelo nome do enum")
    void listOrders() {
        given(orders.findAll(any(), any())).willReturn(new PageImpl<>(List.of(order(OrderStatus.PENDING, payment(PaymentStatus.PENDING), null))));

        bot.handle(message(ADMIN_CHAT, "/pedidos"));
        assertThat(lastText()).contains("Últimos pedidos", "/pedido_12", "R$ 59,90", "Ana &lt;b&gt;Souza&lt;/b&gt;");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(isNull(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
        assertThat(page.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();

        bot.handle(message(ADMIN_CHAT, "/pedidos pendentes"));
        bot.handle(message(ADMIN_CHAT, "/pedidos shipped"));
        verify(orders).findAll(eq(OrderStatus.PENDING), any());
        verify(orders).findAll(eq(OrderStatus.SHIPPED), any());
    }

    @Test
    @DisplayName("/pedidos com status desconhecido ou sem resultados")
    void listOrdersEdgeCases() {
        bot.handle(message(ADMIN_CHAT, "/pedidos voando"));
        assertThat(lastText()).contains("Status desconhecido");
        verifyNoInteractions(orders);

        given(orders.findAll(any(), any())).willReturn(new PageImpl<>(List.of()));
        bot.handle(message(ADMIN_CHAT, "/pedidos"));
        assertThat(lastText()).isEqualTo("Nenhum pedido encontrado.");
    }

    @Test
    @DisplayName("/pedido_12 e /pedido 12 mostram o pedido escapando HTML, com as ações permitidas agora")
    void showOrder() {
        given(orders.findById(12L)).willReturn(order(OrderStatus.PENDING, payment(PaymentStatus.PENDING), null));

        bot.handle(message(ADMIN_CHAT, "/pedido_12"));
        bot.handle(message(ADMIN_CHAT, "/pedido 12"));

        verify(orders, times(2)).findById(12L);
        assertThat(lastText()).contains("<b>Pedido #12</b>", "⏳ Pendente", "Pix aguardando Pix (vence 07/11/2026 16:00)",
                "São Paulo/SP", "2× Caneca &amp; Cia — R$ 59,90", "/usuario_5");
        assertThat(data(lastButtons())).contains("?st:12:CONFIRMED", "?st:12:CANCELLED", "?pay:12", "o:12", "u:5")
                .doesNotContain("?ref:12");
    }

    @Test
    @DisplayName("Pedido pago com devolução pedida: mostra motivo (escapado) e oferece estorno")
    void showOrderWithReturn() {
        given(orders.findById(12L)).willReturn(order(OrderStatus.DELIVERED, payment(PaymentStatus.PAID), NOW));

        bot.handle(message(ADMIN_CHAT, "/pedido 12"));

        assertThat(lastText()).contains("Pix pago em", "Entregue em", "Devolução pedida", "não &lt;serviu&gt;");
        assertThat(data(lastButtons())).contains("?ref:12").doesNotContain("?pay:12").noneMatch(d -> d.startsWith("?st:"));
    }

    @Test
    @DisplayName("Id inválido e pedido inexistente viram mensagens, não erro")
    void invalidIds() {
        bot.handle(message(ADMIN_CHAT, "/pedido abc"));
        bot.handle(message(ADMIN_CHAT, "/pedido -3"));
        verify(client, times(2)).sendMessage(eq(ADMIN_CHAT), contains("Comando inválido"), isNull());

        given(orders.findById(99L)).willThrow(new ResourceNotFoundException("Order not found: 99"));
        bot.handle(message(ADMIN_CHAT, "/pedido 99"));
        assertThat(lastText()).isEqualTo("⚠️ Order not found: 99");
    }

    @Test
    @DisplayName("/usuarios lista ou busca; /usuario mostra detalhes com bloquear e trocar papel")
    void users() {
        given(users.findAll(any(), any())).willReturn(new PageImpl<>(List.of(user(5, Role.ADMIN, false))));
        bot.handle(message(ADMIN_CHAT, "/usuarios"));
        assertThat(lastText()).contains("Últimos cadastros", "/usuario_5", "🚫 bloqueado", "⭐ admin");
        bot.handle(message(ADMIN_CHAT, "/usuarios <ana>"));
        verify(users).findAll(isNull(), any());
        verify(users).findAll(eq("<ana>"), any());
        assertThat(lastText()).contains("Usuários com \"&lt;ana&gt;\"");

        given(users.findAll(any(), any())).willReturn(new PageImpl<>(List.of()));
        bot.handle(message(ADMIN_CHAT, "/usuarios zzz"));
        assertThat(lastText()).isEqualTo("Nenhum usuário encontrado.");

        given(users.findById(5L)).willReturn(user(5, Role.CUSTOMER, true));
        bot.handle(message(ADMIN_CHAT, "/usuario_5"));
        assertThat(lastText()).contains("<b>Usuário #5</b>", "✅ ativo", "Telefone: -", "Papel: cliente", "Pedidos: 3");
        assertThat(data(lastButtons())).containsExactly("?act:5:false", "?role:5:ADMIN", "uo:5", "u:5");
    }

    @Test
    @DisplayName("Na própria conta o bot não oferece bloquear nem trocar papel")
    void ownAccount() {
        given(users.findById(1L)).willReturn(user(1, Role.ADMIN, true));
        bot.handle(message(ADMIN_CHAT, "/usuario 1"));
        assertThat(data(lastButtons())).containsExactly("uo:1", "u:1");
    }

    // ---------- botões ----------

    @Test
    @DisplayName("Botão de ação só pergunta; nada muda até o \"Sim\"")
    void confirmationFirst() {
        bot.handle(click(ADMIN_CHAT, "?st:12:CANCELLED"));

        verify(client).answerCallback("cb-1", null);
        assertThat(lastText()).contains("Mudar o pedido #12 para <b>❌ Cancelado</b>?", "estoque volta");
        assertThat(data(lastButtons())).containsExactly("!st:12:CANCELLED", "x");
        verifyNoInteractions(orders, users);

        bot.handle(click(ADMIN_CHAT, "x"));
        assertThat(lastText()).isEqualTo("Ok, nada foi alterado.");
    }

    @Test
    @DisplayName("Perguntas de confirmação de cada ação")
    void confirmations() {
        assertThat(TelegramMessages.confirmation("st:12:SHIPPED")).contains("🚚 Enviado").doesNotContain("estoque");
        assertThat(TelegramMessages.confirmation("pay:12")).contains("extrato");
        assertThat(TelegramMessages.confirmation("ref:12")).contains("devolvido");
        assertThat(TelegramMessages.confirmation("act:5:false")).contains("Bloquear", "sessões");
        assertThat(TelegramMessages.confirmation("act:5:true")).contains("Desbloquear");
        assertThat(TelegramMessages.confirmation("role:5:ADMIN")).contains("<b>admin</b>");
        assertThat(TelegramMessages.confirmation("role:5:CUSTOMER")).contains("Tirar o acesso");
        assertThat(TelegramMessages.confirmation("drop:5")).isNull();
    }

    @Test
    @DisplayName("Confirmado: muda o status pelo serviço do painel e mostra o pedido atualizado")
    void changeStatus() {
        given(orders.updateStatus(12L, OrderStatus.SHIPPED)).willReturn(order(OrderStatus.SHIPPED, payment(PaymentStatus.PAID), null));

        bot.handle(click(ADMIN_CHAT, "!st:12:SHIPPED"));

        assertThat(lastText()).startsWith("✔ Pedido #12 agora está 🚚 Enviado.");
        assertThat(data(lastButtons())).contains("?st:12:DELIVERED");
    }

    @Test
    @DisplayName("Regra de negócio recusada (ex.: Pix pendente) volta como aviso")
    void businessRuleRejected() {
        given(orders.updateStatus(12L, OrderStatus.SHIPPED)).willThrow(new BusinessException("Order payment is pending"));
        bot.handle(click(ADMIN_CHAT, "!st:12:SHIPPED"));
        assertThat(lastText()).isEqualTo("⚠️ Order payment is pending");
    }

    @Test
    @DisplayName("Confirmar Pix e registrar estorno")
    void payments() {
        given(orders.confirmPayment(12L)).willReturn(order(OrderStatus.CONFIRMED, payment(PaymentStatus.PAID), null));
        bot.handle(click(ADMIN_CHAT, "!pay:12"));
        assertThat(lastText()).startsWith("✔ Pix do pedido #12 confirmado.");

        given(orders.refundPayment(12L)).willReturn(order(OrderStatus.CANCELLED, payment(PaymentStatus.REFUNDED), null));
        bot.handle(click(ADMIN_CHAT, "!ref:12"));
        assertThat(lastText()).startsWith("✔ Estorno do pedido #12 registrado.").contains("Pix estornado");
    }

    @Test
    @DisplayName("Bloquear/desbloquear e trocar papel passam o id do admin (a API impede alterar a si mesmo)")
    void manageUsers() {
        given(users.update(eq(5L), any(), eq(1L))).willReturn(user(5, Role.CUSTOMER, false), user(5, Role.CUSTOMER, true),
                user(5, Role.ADMIN, true), user(5, Role.CUSTOMER, true));

        bot.handle(click(ADMIN_CHAT, "!act:5:false"));
        assertThat(lastText()).startsWith("✔ Usuário #5 bloqueado.");
        bot.handle(click(ADMIN_CHAT, "!act:5:true"));
        assertThat(lastText()).startsWith("✔ Usuário #5 desbloqueado.");
        bot.handle(click(ADMIN_CHAT, "!role:5:ADMIN"));
        assertThat(lastText()).startsWith("✔ Usuário #5 agora é admin.");
        bot.handle(click(ADMIN_CHAT, "!role:5:CUSTOMER"));

        verify(users).update(5L, new UpdateUserRequest(false, null), 1L);
        verify(users).update(5L, new UpdateUserRequest(true, null), 1L);
        verify(users).update(5L, new UpdateUserRequest(null, Role.ADMIN), 1L);
        verify(users).update(5L, new UpdateUserRequest(null, Role.CUSTOMER), 1L);
    }

    @Test
    @DisplayName("Botões de navegação: pedido, usuário e pedidos do usuário")
    void navigation() {
        given(orders.findById(12L)).willReturn(order(OrderStatus.PENDING, null, null));
        bot.handle(click(ADMIN_CHAT, "o:12"));
        assertThat(lastText()).doesNotContain("Pagamento");

        given(users.findById(5L)).willReturn(user(5, Role.CUSTOMER, true));
        bot.handle(click(ADMIN_CHAT, "u:5"));
        assertThat(lastText()).contains("Usuário #5");

        given(users.findOrders(5L)).willReturn(List.of(new OrderResponse(12L, OrderStatus.DELIVERED, new BigDecimal("10.00"),
                NOW, null, List.of(), null, null, null, null, false, false)));
        bot.handle(click(ADMIN_CHAT, "uo:5"));
        assertThat(lastText()).contains("Pedidos do usuário #5", "/pedido_12", "📦 Entregue", "07/11/2026 15:30");

        given(users.findOrders(6L)).willReturn(List.of());
        bot.handle(click(ADMIN_CHAT, "uo:6"));
        assertThat(lastText()).isEqualTo("O usuário #6 não tem pedidos.");
    }

    @Test
    @DisplayName("Dados de botão forjados ou desconhecidos são recusados sem chamar serviços")
    void forgedCallbacks() {
        bot.handle(click(ADMIN_CHAT, "?drop:1"));
        bot.handle(click(ADMIN_CHAT, "!drop:1"));
        bot.handle(click(ADMIN_CHAT, "!st:12:VOANDO"));
        bot.handle(click(ADMIN_CHAT, "o"));
        verify(client, times(4)).sendMessage(eq(ADMIN_CHAT), contains("Comando inválido"), isNull());
        verifyNoInteractions(orders, users);
    }

    @Test
    @DisplayName("Update sem chat (ex.: tipo não suportado) é ignorado")
    void noChat() {
        bot.handle(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("update_id", 1));
        verifyNoInteractions(client, userRepository);
    }

    @Test
    @DisplayName("Formatação: valores nulos e todos os status")
    void formatting() {
        assertThat(TelegramMessages.money(null)).isEqualTo("-");
        assertThat(TelegramMessages.date(null)).isEqualTo("-");
        assertThat(TelegramMessages.esc(null)).isEmpty();
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(TelegramMessages.status(s)).isNotBlank();
            assertThat(TelegramMessages.statusAction(s)).isNotBlank();
        }
        for (PaymentStatus s : PaymentStatus.values()) assertThat(TelegramMessages.payment(s)).isNotBlank();
        var noAddress = new com.sskings.ecommerce.dto.response.AdminOrderResponse(1L, OrderStatus.CANCELLED, BigDecimal.ONE,
                LocalDateTime.of(2026, 1, 1, 0, 0), 2L, "A", "a@a", null, List.of(), List.of(), payment(PaymentStatus.REFUND_PENDING),
                null, null, null);
        assertThat(TelegramMessages.order(noAddress)).doesNotContain("Entrega");
        assertThat(data(TelegramMessages.orderButtons(noAddress))).contains("?ref:1");
        assertThat(PageRequest.of(0, TelegramMessages.PAGE_SIZE).getPageSize()).isEqualTo(10);
    }
}
