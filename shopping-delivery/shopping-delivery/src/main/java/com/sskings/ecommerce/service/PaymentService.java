package com.sskings.ecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.dto.request.PixWebhookRequest;
import com.sskings.ecommerce.dto.response.PaymentResponse;
import com.sskings.ecommerce.event.OrderEvent;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ForbiddenException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.Payment;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.payment.IpAllowlist;
import com.sskings.ecommerce.payment.PixBrCode;
import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.payment.WebhookSignatureVerifier;
import com.sskings.ecommerce.repository.PaymentRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ciclo de vida do pagamento Pix:
 * checkout cria a cobrança (PENDING) → webhook do PSP ou admin confirma (PAID, pedido vai a CONFIRMED)
 * → ou o prazo vence (EXPIRED, pedido CANCELLED e estoque devolvido).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final String TXID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int TXID_LENGTH = 25;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository paymentRepository;
    private final StockService stockService;
    private final PixProperties pix;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    /** Executado dentro da transação do checkout, com o pedido já salvo (tem id e total). */
    public Payment createPixCharge(Order order) {
        LocalDateTime now = LocalDateTime.now(clock);
        String txid = newTxid();
        Payment payment = Payment.builder()
                .order(order)
                .method(PaymentMethod.PIX)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotal())
                .txid(txid)
                .pixPayload(PixBrCode.build(pix.key(), pix.merchantName(), pix.merchantCity(),
                        order.getTotal(), txid, "Pedido " + order.getId()))
                .createdAt(now)
                .expiresAt(now.plusMinutes(pix.expirationMinutes()))
                .build();
        order.setPayment(payment);
        return paymentRepository.save(payment);
    }

    /** Consulta do cliente (polling da tela de pagamento). Só o dono do pedido enxerga. */
    @Transactional
    public PaymentResponse getForCustomer(Long orderId, Long userId) {
        Payment payment = paymentRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.isOverdue(now())) {
            expire(payment);
        }
        return PaymentResponse.from(payment, now());
    }

    /**
     * Notificação do PSP. Origem (allowlist), assinatura e janela de tempo são validadas antes de ler o corpo.
     * Idempotente. Toda rejeição fica na auditoria, mesmo com rollback.
     */
    @Transactional
    public PaymentStatus handleWebhook(String sourceIp, String timestamp, String signature, String rawBody) {
        try {
            return processWebhook(sourceIp, timestamp, signature, rawBody);
        } catch (RuntimeException e) {
            auditService.recordIndependent(null, AuditService.WEBHOOK_REJECTED, "PAYMENT", null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private PaymentStatus processWebhook(String sourceIp, String timestamp, String signature, String rawBody) {
        if (!new IpAllowlist(pix.webhookAllowedIps()).allows(sourceIp)) {
            throw new ForbiddenException("Source IP not allowed");
        }
        signatureVerifier.verify(timestamp, signature, rawBody);
        PixWebhookRequest event = parse(rawBody);
        Payment payment = paymentRepository.findByTxid(event.txid())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getAmount().compareTo(event.amount()) != 0) {
            log.warn("Pix {} paid with wrong amount: expected {}, got {}", payment.getTxid(), payment.getAmount(), event.amount());
            throw new BusinessException("Paid amount does not match the charge");
        }
        switch (payment.getStatus()) {
            case PENDING -> markPaid(payment, event.endToEndId(), "WEBHOOK");
            case EXPIRED, CANCELLED -> {
                // dinheiro entrou para um pedido que já foi cancelado: precisa ser devolvido
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                payment.setEndToEndId(event.endToEndId());
                payment.setPaidAt(now());
                payment.setConfirmedBy("WEBHOOK");
                auditService.record(null, AuditService.PAYMENT_REFUND_PENDING, "ORDER", payment.getOrder().getId(),
                        "Pix received after cancellation, txid " + payment.getTxid());
                log.warn("Pix {} received for cancelled order #{}: refund required", payment.getTxid(), payment.getOrder().getId());
            }
            case PAID, REFUND_PENDING, REFUNDED -> { /* reentrega do mesmo evento: nada a fazer */ }
        }
        return payment.getStatus();
    }

    /** Admin confirma após conferir o extrato (Pix estático, sem PSP). */
    @Transactional
    public void confirmByAdmin(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.isPending()) {
            throw new BusinessException("Payment is not pending");
        }
        markPaid(payment, null, "ADMIN");
        auditService.record(null, AuditService.PAYMENT_CONFIRMED, "ORDER", orderId, "manual, txid " + payment.getTxid());
    }

    /**
     * Admin registra que devolveu o Pix: pagamento com estorno pendente (pedido cancelado após pagar) ou
     * pago com pedido de arrependimento (CDC art. 49).
     */
    @Transactional
    public void refundByAdmin(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        boolean returnRequested = payment.getOrder().getReturnRequestedAt() != null;
        if (payment.getStatus() != PaymentStatus.REFUND_PENDING
                && !(payment.getStatus() == PaymentStatus.PAID && returnRequested)) {
            throw new BusinessException("Payment cannot be refunded");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(now());
        auditService.record(null, AuditService.PAYMENT_REFUNDED, "ORDER", orderId, "txid " + payment.getTxid());
    }

    /** Chamado quando o admin cancela o pedido. */
    public void onOrderCancelled(Order order) {
        Payment payment = order.getPayment();
        if (payment == null) return;
        if (payment.getStatus() == PaymentStatus.PENDING) payment.setStatus(PaymentStatus.CANCELLED);
        else if (payment.getStatus() == PaymentStatus.PAID) payment.setStatus(PaymentStatus.REFUND_PENDING);
    }

    /** Executado periodicamente pelo PixExpirationJob. */
    @Transactional
    public int expireOverdue() {
        List<Payment> overdue = paymentRepository.findAllByStatusAndExpiresAtBefore(PaymentStatus.PENDING, now());
        overdue.forEach(this::expire);
        return overdue.size();
    }

    private void markPaid(Payment payment, String endToEndId, String confirmedBy) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setEndToEndId(endToEndId);
        payment.setPaidAt(now());
        payment.setConfirmedBy(confirmedBy);
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        if ("WEBHOOK".equals(confirmedBy)) {
            auditService.record(null, AuditService.PAYMENT_CONFIRMED, "ORDER", order.getId(),
                    "webhook, txid " + payment.getTxid() + ", e2e " + endToEndId);
            events.publishEvent(new OrderEvent(order.getId(), OrderEvent.Type.PAID));
        }
    }

    private void expire(Payment payment) {
        payment.setStatus(PaymentStatus.EXPIRED);
        Order order = payment.getOrder();
        auditService.record(null, AuditService.PAYMENT_EXPIRED, "ORDER", order.getId(), "txid " + payment.getTxid());
        events.publishEvent(new OrderEvent(order.getId(), OrderEvent.Type.PAYMENT_EXPIRED));
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
            stockService.restore(order);
        }
    }

    private PixWebhookRequest parse(String rawBody) {
        try {
            PixWebhookRequest event = objectMapper.readValue(rawBody, PixWebhookRequest.class);
            if (event == null || !validator.validate(event).isEmpty()) {
                throw new BusinessException("Invalid webhook payload");
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new BusinessException("Invalid webhook payload");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    static String newTxid() {
        StringBuilder sb = new StringBuilder(TXID_LENGTH);
        for (int i = 0; i < TXID_LENGTH; i++) {
            sb.append(TXID_ALPHABET.charAt(RANDOM.nextInt(TXID_ALPHABET.length())));
        }
        return sb.toString();
    }
}
