package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** paymentMethod é opcional e hoje só aceita PIX (padrão). */
public record CheckoutRequest(@NotNull Long addressId, PaymentMethod paymentMethod) {
    public CheckoutRequest(Long addressId) {
        this(addressId, PaymentMethod.PIX);
    }
}
