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
}
