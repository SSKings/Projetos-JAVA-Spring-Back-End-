package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.OrderStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} → {1} = {2}")
    @CsvSource({
            "PENDING,   CONFIRMED, true",
            "PENDING,   CANCELLED, true",
            "PENDING,   SHIPPED,   false",
            "PENDING,   DELIVERED, false",
            "CONFIRMED, SHIPPED,   true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, PENDING,   false",
            "SHIPPED,   DELIVERED, true",
            "SHIPPED,   CANCELLED, false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PENDING,   false",
    })
    void canTransitionTo(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}
