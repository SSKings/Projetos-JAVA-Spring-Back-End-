package com.sskings.ecommerce.model.enums;

/**
 * PENDING        → aguardando o Pix (QR Code válido até expiresAt)
 * PAID           → pagamento confirmado (webhook do PSP ou admin)
 * EXPIRED        → prazo acabou sem pagamento; o pedido é cancelado e o estoque devolvido
 * CANCELLED      → pedido cancelado antes do pagamento
 * REFUND_PENDING → dinheiro recebido mas o pedido foi/está cancelado: precisa de devolução
 * REFUNDED       → devolução feita (registrada pelo admin)
 */
public enum PaymentStatus {
    PENDING, PAID, EXPIRED, CANCELLED, REFUND_PENDING, REFUNDED
}
