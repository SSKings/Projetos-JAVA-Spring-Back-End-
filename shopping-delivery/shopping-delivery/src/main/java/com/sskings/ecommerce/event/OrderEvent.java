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
        PAYMENT_EXPIRED         // Pix venceu: pedido cancelado e estoque devolvido
    }
}
