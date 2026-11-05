package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.response.PaymentResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.InvalidSignatureException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PaymentService paymentService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private static final User ANA = User.builder().id(2L).name("Ana").email("ana@a.com").password("h").build();
    private static final String BODY = "{\"txid\":\"TX1\",\"endToEndId\":\"E1\",\"amount\":20.00}";

    @Test
    @DisplayName("GET /api/orders/{id}/payment devolve o Pix do pedido do usuário")
    void getPayment() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        given(paymentService.getForCustomer(3L, 2L)).willReturn(new PaymentResponse(PaymentMethod.PIX,
                PaymentStatus.PENDING, new BigDecimal("20.00"), "TX1", "000201...", now, now.plusMinutes(30), null, 1800));

        mockMvc.perform(get("/api/orders/3/payment").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pixCopyPaste").value("000201..."))
                .andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    @DisplayName("GET /api/orders/{id}/payment exige login e esconde pedidos de outros (404)")
    void getPayment_authAndOwnership() throws Exception {
        mockMvc.perform(get("/api/orders/3/payment")).andExpect(status().isUnauthorized());

        given(paymentService.getForCustomer(9L, 2L)).willThrow(new ResourceNotFoundException("Payment not found"));
        mockMvc.perform(get("/api/orders/9/payment").with(user(ANA))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Webhook é público (sem JWT) e repassa cabeçalhos e corpo bruto ao serviço")
    void webhook_ok() throws Exception {
        given(paymentService.handleWebhook(any(), eq("1700000000"), eq("abc"), eq(BODY))).willReturn(PaymentStatus.PAID);

        mockMvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentController.TIMESTAMP_HEADER, "1700000000")
                        .header(PaymentController.SIGNATURE_HEADER, "abc")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("Webhook com assinatura inválida → 401; valor divergente → 400")
    void webhook_errors() throws Exception {
        given(paymentService.handleWebhook(any(), any(), eq("bad"), any())).willThrow(new InvalidSignatureException("Invalid webhook signature"));
        given(paymentService.handleWebhook(any(), any(), eq("wrong-amount"), any()))
                .willThrow(new BusinessException("Paid amount does not match the charge"));

        mockMvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentController.SIGNATURE_HEADER, "bad").content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"));
        mockMvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header(PaymentController.SIGNATURE_HEADER, "wrong-amount").content(BODY))
                .andExpect(status().isBadRequest());
    }
}
