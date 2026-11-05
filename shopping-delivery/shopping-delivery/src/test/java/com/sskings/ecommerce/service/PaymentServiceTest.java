package com.sskings.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.InvalidSignatureException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.payment.WebhookSignatureVerifier;
import com.sskings.ecommerce.repository.PaymentRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String SECRET = "s3cret";
    private static final String TS = String.valueOf(NOW.getEpochSecond());

    @Mock PaymentRepository paymentRepository;
    @Mock ProductRepository productRepository;
    @Mock AuditService auditService;

    private PaymentService service;
    private Clock clock;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = service(List.of());

        product = Product.builder().id(1L).name("Caneca").price(BigDecimal.TEN).stockQuantity(3).build();
        order = Order.builder().id(42L).status(OrderStatus.PENDING).total(new BigDecimal("20.00"))
                .items(new ArrayList<>()).build();
        order.getItems().add(OrderItem.builder().id(1L).order(order).product(product).productName("Caneca")
                .quantity(2).unitPrice(BigDecimal.TEN).subtotal(new BigDecimal("20.00")).build());
    }

    private PaymentService service(List<String> allowedIps) {
        PixProperties pix = new PixProperties("pix@loja.com", "ShopEasy", "Sao Paulo", 30, SECRET, allowedIps);
        return new PaymentService(paymentRepository, new StockService(productRepository), pix,
                new WebhookSignatureVerifier(SECRET, clock), new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(), clock, auditService);
    }

    private Payment pending(LocalDateTime expiresAt) {
        Payment p = Payment.builder().id(9L).order(order).method(PaymentMethod.PIX).status(PaymentStatus.PENDING)
                .amount(new BigDecimal("20.00")).txid("TX123").pixPayload("000201...")
                .createdAt(LOCAL_NOW.minusMinutes(5)).expiresAt(expiresAt).build();
        order.setPayment(p);
        return p;
    }

    private PaymentStatus webhook(String body) {
        return service.handleWebhook("10.0.0.1", TS, WebhookSignatureVerifier.sign(SECRET, TS, body), body);
    }

    private static String body(String txid, String amount) {
        return "{\"txid\":\"" + txid + "\",\"endToEndId\":\"E2E001\",\"amount\":" + amount + "}";
    }

    // ---------- criação ----------

    @Test
    @DisplayName("Cria cobrança Pix com o total do pedido, txid aleatório e validade de 30 min")
    void createPixCharge() {
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Payment p = service.createPixCharge(order);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getAmount()).isEqualByComparingTo("20.00");
        assertThat(p.getTxid()).matches("[A-Z0-9]{25}");
        assertThat(p.getExpiresAt()).isEqualTo(LOCAL_NOW.plusMinutes(30));
        assertThat(p.getPixPayload()).contains("br.gov.bcb.pix", "pix@loja.com", "540520.00", p.getTxid(), "Pedido 42");
        assertThat(order.getPayment()).isSameAs(p);
    }

    @Test
    @DisplayName("txids gerados não se repetem")
    void newTxid_unique() {
        assertThat(java.util.stream.IntStream.range(0, 200).mapToObj(i -> PaymentService.newTxid()).distinct()).hasSize(200);
    }

    // ---------- consulta do cliente ----------

    @Test
    @DisplayName("Cliente vê o próprio pagamento pendente com o copia e cola")
    void getForCustomer_pending() {
        pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByOrderIdAndUserId(42L, 1L)).willReturn(Optional.of(order.getPayment()));

        var resp = service.getForCustomer(42L, 1L);

        assertThat(resp.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(resp.pixCopyPaste()).isEqualTo("000201...");
        assertThat(resp.expiresInSeconds()).isEqualTo(600); // relógio do servidor, não do navegador
    }

    @Test
    @DisplayName("Pagamento vencido é expirado na consulta: pedido cancelado, estoque devolvido, sem copia e cola")
    void getForCustomer_overdueExpires() {
        pending(LOCAL_NOW.minusSeconds(1));
        given(paymentRepository.findByOrderIdAndUserId(42L, 1L)).willReturn(Optional.of(order.getPayment()));

        var resp = service.getForCustomer(42L, 1L);

        assertThat(resp.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(resp.pixCopyPaste()).isNull();
        assertThat(resp.expiresInSeconds()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productRepository).incrementStock(1L, 2);
    }

    @Test
    @DisplayName("Pedido de outro usuário: 404")
    void getForCustomer_notOwned() {
        given(paymentRepository.findByOrderIdAndUserId(42L, 2L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForCustomer(42L, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- webhook ----------

    @Test
    @DisplayName("Webhook válido marca como pago e confirma o pedido")
    void webhook_paid() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));

        assertThat(webhook(body("TX123", "20.00"))).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getEndToEndId()).isEqualTo("E2E001");
        assertThat(p.getPaidAt()).isEqualTo(LOCAL_NOW);
        assertThat(p.getConfirmedBy()).isEqualTo("WEBHOOK");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Pix pago logo após o vencimento (antes do job) ainda é aceito")
    void webhook_paidJustAfterExpiry() {
        Payment p = pending(LOCAL_NOW.minusMinutes(1));
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));
        assertThat(webhook(body("TX123", "20"))).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("Reentrega do mesmo evento é idempotente")
    void webhook_idempotent() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));
        webhook(body("TX123", "20.00"));
        order.setStatus(OrderStatus.SHIPPED); // admin já avançou

        assertThat(webhook(body("TX123", "20.00"))).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        p.setStatus(PaymentStatus.REFUND_PENDING);
        assertThat(webhook(body("TX123", "20.00"))).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("Valor diferente da cobrança é recusado e nada muda")
    void webhook_wrongAmount() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));

        assertThatThrownBy(() -> webhook(body("TX123", "2.00")))
                .isInstanceOf(BusinessException.class).hasMessage("Paid amount does not match the charge");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Pix recebido para pedido expirado/cancelado vira estorno pendente")
    void webhook_afterCancellation() {
        Payment p = pending(LOCAL_NOW.minusMinutes(10));
        p.setStatus(PaymentStatus.EXPIRED);
        order.setStatus(OrderStatus.CANCELLED);
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));

        assertThat(webhook(body("TX123", "20.00"))).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(p.getEndToEndId()).isEqualTo("E2E001");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        p.setStatus(PaymentStatus.CANCELLED);
        assertThat(webhook(body("TX123", "20.00"))).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("Assinatura inválida é rejeitada antes de ler o corpo")
    void webhook_invalidSignature() {
        assertThatThrownBy(() -> service.handleWebhook("10.0.0.1", TS, "deadbeef", body("TX123", "20.00")))
                .isInstanceOf(InvalidSignatureException.class);
        verify(paymentRepository, never()).findByTxid(any());
        // rejeição fica na auditoria em transação própria (sobrevive ao rollback)
        verify(auditService).recordIndependent(isNull(), eq(AuditService.WEBHOOK_REJECTED), eq("PAYMENT"), isNull(),
                contains("InvalidSignatureException"));
    }

    @Test
    @DisplayName("Allowlist de IPs do PSP: origem fora da lista → 403 antes de olhar a assinatura")
    void webhook_ipAllowlist() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByTxid("TX123")).willReturn(Optional.of(p));
        PaymentService restricted = service(List.of("200.201.0.0/16", "2001:db8::/32"));
        String body = body("TX123", "20.00");
        String sig = WebhookSignatureVerifier.sign(SECRET, TS, body);

        assertThatThrownBy(() -> restricted.handleWebhook("10.0.0.1", TS, sig, body))
                .isInstanceOf(com.sskings.ecommerce.exception.ForbiddenException.class).hasMessage("Source IP not allowed");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(restricted.handleWebhook("200.201.7.9", TS, sig, body)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("txid desconhecido → 404; corpo inválido ou incompleto → 400")
    void webhook_unknownOrInvalid() {
        given(paymentRepository.findByTxid("NOPE")).willReturn(Optional.empty());
        assertThatThrownBy(() -> webhook(body("NOPE", "1.00"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> webhook("not json")).isInstanceOf(BusinessException.class).hasMessage("Invalid webhook payload");
        assertThatThrownBy(() -> webhook("null")).hasMessage("Invalid webhook payload");
        assertThatThrownBy(() -> webhook("{\"txid\":\"TX123\"}")).hasMessage("Invalid webhook payload");
        assertThatThrownBy(() -> webhook(body("TX123", "-1"))).hasMessage("Invalid webhook payload");
    }

    // ---------- admin ----------

    @Test
    @DisplayName("Admin confirma Pix pendente manualmente")
    void confirmByAdmin() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByOrderId(42L)).willReturn(Optional.of(p));

        service.confirmByAdmin(42L);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getConfirmedBy()).isEqualTo("ADMIN");
        verify(auditService).record(isNull(), eq(AuditService.PAYMENT_CONFIRMED), eq("ORDER"), eq(42L), contains("manual"));
        assertThat(p.getEndToEndId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Admin não confirma pagamento que não está pendente nem pedido sem pagamento")
    void confirmByAdmin_invalid() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        p.setStatus(PaymentStatus.EXPIRED);
        given(paymentRepository.findByOrderId(42L)).willReturn(Optional.of(p));
        given(paymentRepository.findByOrderId(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmByAdmin(42L)).hasMessage("Payment is not pending");
        assertThatThrownBy(() -> service.confirmByAdmin(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Cancelar o pedido: Pix pendente é cancelado; Pix pago vira estorno pendente; sem pagamento nada muda")
    void onOrderCancelled() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        service.onOrderCancelled(order);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        p.setStatus(PaymentStatus.PAID);
        service.onOrderCancelled(order);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        p.setStatus(PaymentStatus.EXPIRED);
        service.onOrderCancelled(order);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        order.setPayment(null);
        service.onOrderCancelled(order); // não lança
    }

    // ---------- expiração ----------

    @Test
    @DisplayName("Job expira cobranças vencidas, cancela o pedido e devolve o estoque")
    void expireOverdue() {
        Payment p = pending(LOCAL_NOW.minusMinutes(1));
        Order shipped = Order.builder().id(43L).status(OrderStatus.SHIPPED).items(List.of()).build();
        Payment other = Payment.builder().order(shipped).status(PaymentStatus.PENDING).build();
        given(paymentRepository.findAllByStatusAndExpiresAtBefore(PaymentStatus.PENDING, LOCAL_NOW))
                .willReturn(List.of(p, other));

        assertThat(service.expireOverdue()).isEqualTo(2);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productRepository).incrementStock(1L, 2);
        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED); // só cancela pedidos ainda pendentes
    }

    // ---------- estorno ----------

    @Test
    @DisplayName("Admin registra estorno de Pix com estorno pendente ou pago com pedido de arrependimento")
    void refundByAdmin() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByOrderId(42L)).willReturn(Optional.of(p));

        p.setStatus(PaymentStatus.REFUND_PENDING);
        service.refundByAdmin(42L);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.getRefundedAt()).isEqualTo(LOCAL_NOW);

        p.setStatus(PaymentStatus.PAID);
        order.setReturnRequestedAt(LOCAL_NOW);
        service.refundByAdmin(42L);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(auditService, org.mockito.Mockito.times(2)).record(isNull(), eq(AuditService.PAYMENT_REFUNDED), eq("ORDER"), eq(42L), any());
    }

    @Test
    @DisplayName("Não há estorno de Pix pago sem arrependimento, nem de pendente; sem pagamento → 404")
    void refundByAdmin_invalid() {
        Payment p = pending(LOCAL_NOW.plusMinutes(10));
        given(paymentRepository.findByOrderId(42L)).willReturn(Optional.of(p));
        given(paymentRepository.findByOrderId(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundByAdmin(42L)).hasMessage("Payment cannot be refunded");
        p.setStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> service.refundByAdmin(42L)).hasMessage("Payment cannot be refunded");
        assertThatThrownBy(() -> service.refundByAdmin(7L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
