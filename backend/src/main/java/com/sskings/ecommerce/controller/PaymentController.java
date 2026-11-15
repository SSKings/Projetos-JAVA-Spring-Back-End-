package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.dto.response.PaymentResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    public static final String TIMESTAMP_HEADER = "X-Pix-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Pix-Signature";

    private final PaymentService paymentService;

    @GetMapping("/api/orders/{id}/payment")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(paymentService.getForCustomer(id, user.getId()));
    }

    /**
     * Rota pública (o PSP não tem JWT): a autenticação é a assinatura HMAC do corpo bruto.
     * O corpo é recebido como String para que a assinatura seja verificada sobre os bytes exatos enviados.
     */
    @PostMapping(value = "/api/payments/pix/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, PaymentStatus>> pixWebhook(
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody,
            jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("status",
                paymentService.handleWebhook(request.getRemoteAddr(), timestamp, signature, rawBody)));
    }
}
