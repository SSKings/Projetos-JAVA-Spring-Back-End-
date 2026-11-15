package com.sskings.ecommerce.mail;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Textos dos e-mails (ADR-025). Texto puro: nada do que o cliente digitou vira HTML. Nenhum e-mail pede senha,
 * código ou dado de pagamento — só leva a links da própria loja (o cliente pode conferir o endereço).
 */
public final class MailMessages {

    // Tokens vão no FRAGMENTO do link (#token=...): o navegador não manda o fragmento ao servidor, então ele não fica
    // no log de acesso do nginx nem em proxies — com "?token=", quem lesse o log usaria um link de nova senha válido.

    public record Mail(String subject, String body) {}

    public record Item(String name, int quantity, BigDecimal subtotal) {}

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private MailMessages() {}

    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value).replace('\u00A0', ' ');
    }

    /**
     * Só um primeiro nome "de verdade" (letras, apóstrofo, hífen) entra no e-mail. O nome é digitado por quem se
     * cadastra — que pode usar o e-mail de OUTRA pessoa: "http://site-falso" como nome virava "Olá http://site-falso!"
     * num e-mail legítimo da loja para a vítima.
     */
    static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        String first = name.trim().split("\\s+")[0];
        return first.matches("\\p{L}[\\p{L}'-]{0,29}") ? " " + first : "";
    }

    private static String footer(MailProperties p) {
        return "\n\n— Equipe " + p.storeName() + "\nE-mail automático: não responda. Nunca pedimos sua senha, código de verificação ou dados de"
                + " pagamento por e-mail.";
    }

    // ---------- conta ----------

    public static Mail verifyEmail(MailProperties p, String name, String token, int hours) {
        return new Mail("Confirme seu e-mail — " + p.storeName(),
                "Olá" + firstName(name) + "!\n\nPara confirmar seu e-mail e poder fazer pedidos, abra o link abaixo (vale "
                        + hours + " horas):\n\n" + p.link("/verificar-email#token=" + token)
                        + "\n\nSe você não criou uma conta na " + p.storeName() + ", ignore este e-mail." + footer(p));
    }

    public static Mail resetPassword(MailProperties p, String name, String token, int minutes) {
        return new Mail("Redefinição de senha — " + p.storeName(),
                "Olá" + firstName(name) + "!\n\nRecebemos um pedido para redefinir a senha da sua conta. Para criar uma nova"
                        + " senha, abra o link abaixo (vale " + minutes + " minutos e funciona uma vez só):\n\n"
                        + p.link("/redefinir-senha#token=" + token)
                        + "\n\nSe não foi você, ignore este e-mail: sua senha continua a mesma." + footer(p));
    }

    public static Mail security(MailProperties p, String name, String what, LocalDateTime when) {
        return new Mail("Alerta de segurança: " + what + " — " + p.storeName(),
                "Olá" + firstName(name) + "!\n\n" + what + " na sua conta em " + WHEN.format(when)
                        + ".\n\nSe foi você, está tudo certo. Se NÃO foi você, redefina sua senha agora em "
                        + p.link("/esqueci-senha") + " e fale com a loja." + footer(p));
    }

    // ---------- pedidos ----------

    private static String items(List<Item> items, BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) sb.append("  ").append(i.quantity()).append("× ").append(i.name()).append(" — ").append(money(i.subtotal())).append('\n');
        return sb.append("  Total: ").append(money(total)).toString();
    }

    public static Mail orderCreated(MailProperties p, String name, long orderId, List<Item> items, BigDecimal total,
                                    String pixCopyPaste, LocalDateTime expiresAt) {
        return new Mail("Pedido #" + orderId + " recebido — pague o Pix até " + WHEN.format(expiresAt),
                "Olá" + firstName(name) + "!\n\nRecebemos seu pedido #" + orderId + ":\n\n" + items(items, total)
                        + "\n\nPara concluir, pague o Pix até " + WHEN.format(expiresAt) + ". Pix copia e cola:\n\n" + pixCopyPaste
                        + "\n\nO QR Code e o status do pagamento estão em " + p.link("/orders/" + orderId)
                        + ". Se o Pix não for pago até lá, o pedido é cancelado automaticamente." + footer(p));
    }

    public static Mail orderPaid(MailProperties p, String name, long orderId, BigDecimal total) {
        return new Mail("Pagamento confirmado — pedido #" + orderId,
                "Olá" + firstName(name) + "!\n\nO Pix de " + money(total) + " do pedido #" + orderId + " foi confirmado."
                        + " Já estamos separando seu pedido.\n\nAcompanhe em " + p.link("/orders/" + orderId) + footer(p));
    }

    public static Mail orderShipped(MailProperties p, String name, long orderId) {
        return new Mail("Pedido #" + orderId + " enviado",
                "Olá" + firstName(name) + "!\n\nSeu pedido #" + orderId + " saiu para entrega.\n\nAcompanhe em "
                        + p.link("/orders/" + orderId) + footer(p));
    }

    public static Mail orderDelivered(MailProperties p, String name, long orderId) {
        return new Mail("Pedido #" + orderId + " entregue",
                "Olá" + firstName(name) + "!\n\nSeu pedido #" + orderId + " foi entregue. Esperamos que goste!\n\n"
                        + "Se mudar de ideia, você pode devolver em até 7 dias (direito de arrependimento, CDC art. 49) em "
                        + p.link("/orders/" + orderId) + footer(p));
    }

    public static Mail orderCancelled(MailProperties p, String name, long orderId, String reason, boolean refundDue) {
        return new Mail("Pedido #" + orderId + " cancelado",
                "Olá" + firstName(name) + "!\n\nSeu pedido #" + orderId + " foi cancelado (" + reason + ")."
                        + (refundDue ? " O Pix já tinha sido pago: vamos devolver o valor pelo mesmo Pix e avisar quando o estorno for feito." : "")
                        + "\n\nDetalhes em " + p.link("/orders/" + orderId) + footer(p));
    }

    public static Mail returnRequested(MailProperties p, String name, long orderId) {
        return new Mail("Devolução solicitada — pedido #" + orderId,
                "Olá" + firstName(name) + "!\n\nRecebemos seu pedido de devolução do pedido #" + orderId
                        + ". A loja vai entrar em contato com as instruções. Assim que o produto voltar, o valor é devolvido"
                        + " pelo mesmo Pix.\n\nAcompanhe em " + p.link("/orders/" + orderId) + footer(p));
    }

    public static Mail refunded(MailProperties p, String name, long orderId, BigDecimal total) {
        return new Mail("Estorno feito — pedido #" + orderId,
                "Olá" + firstName(name) + "!\n\nO valor de " + money(total) + " do pedido #" + orderId
                        + " foi devolvido para a conta de onde saiu o Pix." + footer(p));
    }
}
