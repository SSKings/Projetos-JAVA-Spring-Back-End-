package com.sskings.ecommerce.model.enums;

import java.util.Set;

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    /** Fluxo permitido: PENDING → CONFIRMED → SHIPPED → DELIVERED, cancelamento antes do envio. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> Set.of(CONFIRMED, CANCELLED).contains(next);
            case CONFIRMED -> Set.of(SHIPPED, CANCELLED).contains(next);
            case SHIPPED   -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
