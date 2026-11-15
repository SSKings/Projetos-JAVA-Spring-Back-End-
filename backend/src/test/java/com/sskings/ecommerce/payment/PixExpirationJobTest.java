package com.sskings.ecommerce.payment;

import com.sskings.ecommerce.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class PixExpirationJobTest {

    @Test
    @DisplayName("Job delega a expiração ao PaymentService (com e sem cobranças vencidas)")
    void run() {
        PaymentService service = mock(PaymentService.class);
        given(service.expireOverdue()).willReturn(2, 0);
        PixExpirationJob job = new PixExpirationJob(service);

        job.run();
        job.run();

        verify(service, times(2)).expireOverdue();
    }
}
