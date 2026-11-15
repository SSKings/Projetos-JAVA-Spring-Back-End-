package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.AuditLog;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private final AuditLogRepository repo = mock(AuditLogRepository.class);
    private final AuditService service = new AuditService(repo, Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Grava ator explícito, alvo, detalhes (até 1000 caracteres), data e IP da requisição")
    void explicitActor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record(User.builder().id(5L).email("ana@a.com").build(), "X", "ORDER", 9L, "d".repeat(1200));

        AuditLog log = saved();
        assertThat(log.getActorId()).isEqualTo(5L);
        assertThat(log.getActorEmail()).isEqualTo("ana@a.com");
        assertThat(log.getTargetId()).isEqualTo("9");
        assertThat(log.getDetails()).hasSize(1000);
        assertThat(log.getIp()).isEqualTo("203.0.113.7");
        assertThat(log.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 12, 0));
    }

    @Test
    @DisplayName("Sem ator explícito usa o usuário autenticado; fora de requisição (job) fica sem ator e sem IP")
    void implicitActor() {
        User admin = User.builder().id(1L).email("admin@x.com").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));
        service.recordIndependent(null, "Y", null, null, null);
        assertThat(saved().getActorEmail()).isEqualTo("admin@x.com");

        SecurityContextHolder.clearContext();
        service.record(null, "Z", null, null, null);
        AuditLog log = saved();
        assertThat(log.getActorId()).isNull();
        assertThat(log.getIp()).isNull();
        assertThat(log.getTargetId()).isNull();
    }

    @Test
    @DisplayName("Busca com e sem filtro de ação; mascaramento de email")
    void searchAndMask() {
        var page = PageRequest.of(0, 10);
        when(repo.findAll(page)).thenReturn(new PageImpl<>(List.of()));
        when(repo.findAllByAction("LOGIN_FAILED", page)).thenReturn(new PageImpl<>(List.of()));
        service.search(null, page);
        service.search(" ", page);
        service.search("LOGIN_FAILED", page);
        verify(repo, times(2)).findAll(page);
        verify(repo).findAllByAction("LOGIN_FAILED", page);

        assertThat(AuditService.maskEmail("ana.souza@x.com")).isEqualTo("a***@x.com");
        assertThat(AuditService.maskEmail("semarroba")).isEqualTo("***");
        assertThat(AuditService.maskEmail(null)).isNull();
    }

    // ---------- log dos eventos de segurança (análise de logs, 2026-11-15) ----------

    private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captureSecurityLog() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("security.audit");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @AfterEach
    void detachAppenders() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("security.audit")).detachAndStopAllAppenders();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static java.util.Map<String, Object> keyValues(ch.qos.logback.classic.spi.ILoggingEvent e) {
        java.util.Map<String, Object> kv = new java.util.HashMap<>();
        if (e.getKeyValuePairs() != null) e.getKeyValuePairs().forEach(p -> kv.put(p.key, p.value));
        return kv;
    }

    @Test
    @DisplayName("Falha de login: WARN em security.audit com ação, IP e e-mail mascarado — mesmo se o banco falhar")
    void securityFailureIsLoggedEvenIfDatabaseFails() {
        var log = captureSecurityLog();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        when(repo.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("banco fora"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recordIndependent(null, AuditService.LOGIN_FAILED, "USER", null,
                "unknown email " + AuditService.maskEmail("ana.souza@teste.com"))).isInstanceOf(RuntimeException.class);

        assertThat(log.list).hasSize(1);
        var e = log.list.get(0);
        assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(e.getFormattedMessage()).contains("LOGIN_FAILED", "ip=203.0.113.9", "a***@teste.com").doesNotContain("ana.souza");
        assertThat(keyValues(e)).containsEntry("event.action", "LOGIN_FAILED").containsEntry("source.ip", "203.0.113.9");
    }

    @Test
    @DisplayName("Mudança sensível (senha alterada): INFO só depois do commit; com rollback, nenhum log")
    void sensitiveChangeIsLoggedOnlyAfterCommit() {
        var log = captureSecurityLog();
        User ana = User.builder().id(7L).email("ana.souza@teste.com").build();

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        service.record(ana, AuditService.PASSWORD_CHANGED, "USER", 7L, null);
        assertThat(log.list).as("antes do commit").isEmpty();
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        assertThat(log.list).hasSize(1);
        assertThat(log.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
        assertThat(log.list.get(0).getFormattedMessage()).contains("PASSWORD_CHANGED", "ator=#7 a***@teste.com", "alvo=USER#7")
                .doesNotContain("ana.souza");
        assertThat(keyValues(log.list.get(0))).containsEntry("user.id", 7L).containsEntry("event.target", "USER#7");

        log.list.clear();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        service.record(ana, AuditService.MFA_DISABLED, "USER", 7L, null);
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        assertThat(log.list).as("rollback").isEmpty();
    }

    @Test
    @DisplayName("Eventos que não são de segurança (produto, pedido, perfil) ficam só no banco")
    void otherEventsAreNotLogged() {
        var log = captureSecurityLog();
        service.record(null, AuditService.PRODUCT_CHANGED, "PRODUCT", 3L, "created");
        service.record(null, AuditService.ORDER_STATUS_CHANGED, "ORDER", 4L, "PENDING -> CONFIRMED");
        assertThat(log.list).isEmpty();
        verify(repo, times(2)).save(any());
        assertThat(AuditService.WARN_EVENTS).doesNotContainAnyElementsOf(AuditService.INFO_EVENTS);
    }
}
