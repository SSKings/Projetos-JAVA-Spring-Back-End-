package com.sskings.ecommerce.service;

import com.sskings.ecommerce.repository.AuditLogRepository;
import com.sskings.ecommerce.repository.TrustedLoginSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Apaga dados pessoais fora do prazo (LGPD, minimização — revisão 5, R5-5). Antes, a auditoria (IP e e-mail) crescia
 * para sempre e as origens confiáveis de quem nunca mais entrava não eram apagadas.
 * <ul>
 *   <li>auditoria: {@code AUDIT_RETENTION_DAYS} (padrão 365; mínimo 180 — Marco Civil, art. 15, pede 6 meses de
 *       registros de acesso). O prazo final é decisão jurídica: ajuste com o advogado/DPO;</li>
 *   <li>origens confiáveis: 90 dias sem login (TrustedLoginSources.TTL_DAYS).</li>
 * </ul>
 * Logs de acesso do Tomcat têm o próprio prazo (max-days: 190, application-prod.yml).
 */
@Slf4j
@Component
public class DataRetentionJob {

    static final int MIN_AUDIT_DAYS = 180;

    private final AuditLogRepository auditLogs;
    private final TrustedLoginSourceRepository trustedSources;
    private final com.sskings.ecommerce.repository.EmailTokenRepository emailTokens;
    private final Clock clock;
    private final int auditDays;

    public DataRetentionJob(AuditLogRepository auditLogs, TrustedLoginSourceRepository trustedSources,
                            com.sskings.ecommerce.repository.EmailTokenRepository emailTokens, Clock clock,
                            @Value("${app.retention.audit-days:365}") int auditDays) {
        if (auditDays < MIN_AUDIT_DAYS) {
            throw new IllegalStateException("app.retention.audit-days must be at least " + MIN_AUDIT_DAYS
                    + " (Marco Civil da Internet, art. 15: 6 months of access records)");
        }
        this.auditLogs = auditLogs;
        this.trustedSources = trustedSources;
        this.emailTokens = emailTokens;
        this.clock = clock;
        this.auditDays = auditDays;
    }

    /** Uma vez por dia; a primeira 1 min depois do start. */
    @Scheduled(initialDelayString = "${app.retention.initial-delay-ms:60000}", fixedDelayString = "${app.retention.interval-ms:86400000}")
    public void purge() {
        LocalDateTime now = LocalDateTime.now(clock);
        int audit = auditLogs.deleteOlderThan(now.minusDays(auditDays));
        int sources = trustedSources.deleteOlderThan(now.minusDays(TrustedLoginSources.TTL_DAYS));
        emailTokens.deleteExpiredBefore(now.minusDays(1));   // links vencidos não servem para nada
        if (audit + sources > 0) {
            log.info("Retenção: {} evento(s) de auditoria com mais de {} dias e {} origem(ns) confiável(is) vencida(s) apagados",
                    audit, auditDays, sources);
        }
    }
}
