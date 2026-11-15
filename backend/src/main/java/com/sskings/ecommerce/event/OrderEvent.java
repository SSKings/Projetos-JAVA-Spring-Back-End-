package com.sskings.ecommerce.event;

/**
 * Fato de negócio de um pedido, publicado dentro da transação que o causou. Quem escuta com
 * {@code @TransactionalEventListener} (padrão AFTER_COMMIT) só é chamado se a transação for confirmada:
 * um checkout que falhou e deu rollback não gera aviso de "novo pedido".
 */
public record OrderEvent(Long orderId, Type type) {

    public enum Type {
        CREATED,                // checkout concluído (Pix aguardando pagamento)
        PAID,                   // Pix confirmado pelo PSP (webhook): pedido pronto para separar
        CANCELLED_BY_CUSTOMER,  // cliente cancelou antes do envio
        RETURN_REQUESTED,       // direito de arrependimento (CDC art. 49)
        PAYMENT_EXPIRED,        // Pix venceu: pedido cancelado e estoque devolvido
        // Causados pelo próprio admin (o bot não avisa os admins; o cliente recebe e-mail — ADR-025)
        PAID_MANUALLY,          // admin confirmou o Pix olhando o extrato
        SHIPPED,                // admin marcou como enviado
        DELIVERED,              // admin marcou como entregue (começa o prazo de arrependimento)
        CANCELLED_BY_STORE,     // admin cancelou
        REFUNDED                // admin registrou o estorno do Pix
    }

    /** Ação do próprio admin: o bot do Telegram não avisa os admins (quem fez já sabe). */
    public boolean byAdmin() {
        return switch (type) {
            case PAID_MANUALLY, SHIPPED, DELIVERED, CANCELLED_BY_STORE, REFUNDED -> true;
            default -> false;
        };
    }
}
