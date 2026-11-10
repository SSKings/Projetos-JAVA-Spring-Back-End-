package com.sskings.ecommerce.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailMessagesTest {

    private final MailProperties p = new MailProperties(true, "loja@gmail.com", "https://loja.com.br/", false, "Loja Exemplo");

    @Test
    @DisplayName("Links usam o endereço público (sem barra dupla); nenhum e-mail pede senha ou código")
    void linksAndSafety() {
        var verify = MailMessages.verifyEmail(p, "Ana Souza", "TOKEN", 48);
        assertThat(verify.body()).contains("Olá Ana!", "https://loja.com.br/verificar-email#token=TOKEN", "48 horas", "Equipe Loja Exemplo");
        var reset = MailMessages.resetPassword(p, "Ana", "TOK2", 30);
        assertThat(reset.subject()).contains("Redefinição de senha");
        assertThat(reset.body()).contains("https://loja.com.br/redefinir-senha#token=TOK2", "30 minutos", "uma vez só",
                "Se não foi você, ignore");
        for (var m : List.of(verify, reset)) {
            assertThat(m.body()).contains("Nunca pedimos sua senha").doesNotContain("<", ">");
        }
    }

    @Test
    @DisplayName("Token no fragmento (#): o navegador não o manda ao servidor, então não cai no log do nginx")
    void tokenInFragment() {
        assertThat(MailMessages.verifyEmail(p, "Ana", "TOKEN", 48).body()).doesNotContain("?token=");
        assertThat(MailMessages.resetPassword(p, "Ana", "TOKEN", 30).body()).doesNotContain("?token=");
    }

    @Test
    @DisplayName("Nome digitado no cadastro não vira texto de golpe no e-mail: só primeiro nome com letras")
    void nameIsSanitized() {
        for (String evil : new String[]{"http://site-falso.example/login Silva", "www.golpe.com", "Pague@aqui", "Ana123",
                "<b>Ana</b>", "x".repeat(31), "😀 Ana"}) {
            assertThat(MailMessages.verifyEmail(p, evil, "T", 48).body()).as(evil).startsWith("Olá!\n").doesNotContain("golpe", "falso");
        }
        for (String ok : new String[]{"Ana", "  Conceição Souza", "D'Ávila", "Ana-Maria Lima"}) {
            assertThat(MailMessages.firstName(ok)).as(ok).isEqualTo(" " + ok.trim().split("\\s+")[0]);
        }
    }

    @Test
    @DisplayName("Pedido recebido: itens, total em reais, Pix copia e cola, prazo e link do pedido")
    void orderCreated() {
        var m = MailMessages.orderCreated(p, null, 12, List.of(new MailMessages.Item("Caneca", 2, new BigDecimal("59.80"))),
                new BigDecimal("1234.5"), "00020126PIX", LocalDateTime.of(2026, 11, 10, 15, 30));
        assertThat(m.subject()).isEqualTo("Pedido #12 recebido — pague o Pix até 10/11/2026 às 15:30");
        assertThat(m.body()).contains("Olá!", "2× Caneca — R$ 59,80", "Total: R$ 1.234,50", "00020126PIX",
                "https://loja.com.br/orders/12");
    }

    @Test
    @DisplayName("Demais avisos de pedido e de segurança")
    void others() {
        assertThat(MailMessages.orderPaid(p, "Ana", 3, BigDecimal.TEN).body()).contains("R$ 10,00", "confirmado");
        assertThat(MailMessages.orderShipped(p, "Ana", 3).subject()).isEqualTo("Pedido #3 enviado");
        assertThat(MailMessages.orderDelivered(p, "Ana", 3).body()).contains("7 dias", "CDC art. 49");
        assertThat(MailMessages.orderCancelled(p, "Ana", 3, "pela loja", true).body()).contains("pela loja", "devolver o valor");
        assertThat(MailMessages.orderCancelled(p, "Ana", 3, "a seu pedido", false).body()).doesNotContain("devolver o valor");
        assertThat(MailMessages.returnRequested(p, "Ana", 3).subject()).contains("Devolução");
        assertThat(MailMessages.refunded(p, "Ana", 3, BigDecimal.ONE).body()).contains("R$ 1,00", "devolvido");
        assertThat(MailMessages.security(p, "Ana", "Sua senha foi alterada", LocalDateTime.of(2026, 11, 10, 9, 5)).body())
                .contains("Sua senha foi alterada na sua conta em 10/11/2026 às 09:05", "https://loja.com.br/esqueci-senha");
    }
}
