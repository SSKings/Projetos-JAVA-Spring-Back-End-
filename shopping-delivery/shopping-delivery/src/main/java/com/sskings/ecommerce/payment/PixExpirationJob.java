package com.sskings.ecommerce.payment;

import com.sskings.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cancela pedidos cujo Pix venceu sem pagamento e devolve o estoque. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PixExpirationJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.pix.expiration-check-ms:60000}")
    public void run() {
        int expired = paymentService.expireOverdue();
        if (expired > 0) {
            log.info("{} Pix charge(s) expired; orders cancelled and stock restored", expired);
        }
    }
}
