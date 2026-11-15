package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.sskings.ecommerce.model.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} → {1} = {2}")
    @CsvSource({
            "PENDING,   PENDING,   false",
            "PENDING,   CONFIRMED, true",
            "PENDING,   SHIPPED,   true",
            "PENDING,   DELIVERED, true",
            "PENDING,   CANCELLED, true",
            "CONFIRMED, PENDING,   false",
            "CONFIRMED, CONFIRMED, false",
            "CONFIRMED, SHIPPED,   true",
            "CONFIRMED, DELIVERED, true",
            "CONFIRMED, CANCELLED, true",
            "SHIPPED,   PENDING,   false",
            "SHIPPED,   CONFIRMED, false",
            "SHIPPED,   DELIVERED, true",
            "SHIPPED,   CANCELLED, false",
            "DELIVERED, SHIPPED,   false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PENDING,   false",
            "CANCELLED, DELIVERED, false",
    })
    void canTransitionTo(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("nextStatuses lista as transições permitidas na ordem do fluxo")
    void nextStatuses() {
        assertThat(PENDING.nextStatuses()).containsExactly(CONFIRMED, SHIPPED, DELIVERED, CANCELLED);
        assertThat(CONFIRMED.nextStatuses()).containsExactly(SHIPPED, DELIVERED, CANCELLED);
        assertThat(SHIPPED.nextStatuses()).containsExactly(DELIVERED);
        assertThat(DELIVERED.nextStatuses()).isEmpty();
        assertThat(CANCELLED.nextStatuses()).isEmpty();
    }
}
