package com.sskings.ecommerce.model.enums;

import java.util.Arrays;
import java.util.List;

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    /**
     * O pedido só anda para frente em PENDING → CONFIRMED → SHIPPED → DELIVERED, podendo pular etapas
     * (ex.: retirada na loja vai de PENDING direto para DELIVERED). Cancelar só é possível antes do envio.
     * DELIVERED e CANCELLED são finais.
     */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next != PENDING;
            case CONFIRMED -> next == SHIPPED || next == DELIVERED || next == CANCELLED;
            case SHIPPED   -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Status para os quais o pedido pode ir a partir deste — exposto ao front-end para não duplicar a regra. */
    public List<OrderStatus> nextStatuses() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }
}
