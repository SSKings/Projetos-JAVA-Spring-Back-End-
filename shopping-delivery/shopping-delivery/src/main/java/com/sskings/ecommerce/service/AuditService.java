package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.AuditLog;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Trilha de auditoria.
 * O ator, quando não informado, é o usuário autenticado da requisição (null = sistema/PSP).
 * - record(): na mesma transação da ação (se a ação falhar e der rollback, o registro some junto).
 * - recordIndependent(): em transação própria, para eventos de segurança que acompanham uma falha
 *   (login recusado, webhook rejeitado) e precisam ficar gravados mesmo com o rollback da ação.
 * Eventos de segurança também vão para o LOG (logger "security.audit"): no banco, não apareciam no Loki nem no
 * `docker logs`, e se o banco estivesse fora do ar o registro se perdia (análise de logs, 2026-11-15).
 * - falhas e ataques (WARN): escritos ANTES de gravar no banco — ficam mesmo se o banco falhar;
 * - mudanças sensíveis que deram certo (INFO): escritos só DEPOIS do commit — rollback não deixa log de algo que não
 *   aconteceu.
 * E-mail sempre mascarado; os campos também vão como pares chave-valor (viram campos no JSON de produção).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGIN_ATTACK_SUSPECTED = "LOGIN_ATTACK_SUSPECTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String MFA_SETUP_STARTED = "MFA_SETUP_STARTED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTER = "REGISTER";
    public static final String MFA_ENABLED = "MFA_ENABLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String ORDER_CANCELLED_BY_CUSTOMER = "ORDER_CANCELLED_BY_CUSTOMER";
    public static final String RETURN_REQUESTED = "RETURN_REQUESTED";
    public static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    public static final String PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
    public static final String PAYMENT_REFUND_PENDING = "PAYMENT_REFUND_PENDING";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    public static final String WEBHOOK_REJECTED = "WEBHOOK_REJECTED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String PRODUCT_CHANGED = "PRODUCT_CHANGED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String DATA_EXPORTED = "DATA_EXPORTED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";

    /** Logger próprio: filtra-se "security.audit" no Loki/jq sem depender do nome das classes. */
    static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.audit");

    /** Falhas e ataques: WARN, aparecem no painel de erros/avisos do Grafana. */
    static final Set<String> WARN_EVENTS = Set.of(LOGIN_FAILED, ACCOUNT_LOCKED, LOGIN_ATTACK_SUSPECTED, WEBHOOK_REJECTED);

    /** Mudanças sensíveis bem-sucedidas: INFO. O resto (pedido, produto, perfil) fica só no banco. */
    static final Set<String> INFO_EVENTS = Set.of(LOGIN_SUCCESS, PASSWORD_CHANGED, PASSWORD_RESET, MFA_ENABLED, MFA_DISABLED,
            USER_UPDATED, ACCOUNT_DELETED, DATA_EXPORTED, PAYMENT_CONFIRMED, PAYMENT_REFUNDED);

    private final AuditLogRepository repository;
    private final Clock clock;

    @Transactional
    public void record(User actor, String action, String targetType, Object targetId, String details) {
        AuditLog entry = save(actor, action, targetType, targetId, details);
        afterCommit(() -> log(entry));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(User actor, String action, String targetType, Object targetId, String details) {
        AuditLog entry = entry(actor != null ? actor : currentUser(), action, targetType, targetId, details);
        log(entry);                       // antes de gravar: com o banco fora do ar, o log ainda fica
        repository.save(entry);
    }

    /** Registra o evento no log se ele for de segurança (WARN_EVENTS / INFO_EVENTS); os outros ficam só no banco. */
    static void log(AuditLog e) {
        Level level = WARN_EVENTS.contains(e.getAction()) ? Level.WARN : INFO_EVENTS.contains(e.getAction()) ? Level.INFO : null;
        if (level == null) return;
        String actor = e.getActorId() == null ? "-" : "#" + e.getActorId() + " " + maskEmail(e.getActorEmail());
        String target = e.getTargetType() == null ? "-" : e.getTargetType() + (e.getTargetId() == null ? "" : "#" + e.getTargetId());
        SECURITY_LOG.atLevel(level)
                .addKeyValue("event.action", e.getAction())
                .addKeyValue("user.id", e.getActorId())
                .addKeyValue("source.ip", e.getIp())
                .addKeyValue("event.target", target)
                .log("{} ator={} alvo={} ip={}{}", e.getAction(), actor, target, e.getIp() == null ? "-" : e.getIp(),
                        e.getDetails() == null ? "" : " — " + e.getDetails());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, Pageable pageable) {
        return action == null || action.isBlank()
                ? repository.findAll(pageable)
                : repository.findAllByAction(action, pageable);
    }

    private AuditLog save(User explicitActor, String action, String targetType, Object targetId, String details) {
        AuditLog entry = entry(explicitActor != null ? explicitActor : currentUser(), action, targetType, targetId, details);
        repository.save(entry);
        return entry;
    }

    private AuditLog entry(User actor, String action, String targetType, Object targetId, String details) {
        return AuditLog.builder()
                .createdAt(LocalDateTime.now(clock))
                .actorId(actor != null ? actor.getId() : null)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .action(action)
                .targetType(targetType)
                .targetId(targetId != null ? String.valueOf(targetId) : null)
                .details(details != null && details.length() > 1000 ? details.substring(0, 1000) : details)
                .ip(currentIp())
                .build();
    }

    /** Usuário autenticado da requisição (admin nas ações administrativas); null em jobs e webhooks. */
    static User currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user ? user : null;
    }

    static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;   // job agendado
    }

    /** "ana.souza@x.com" → "a***@x.com" (email tentado no login, que pode nem ser de um usuário). */
    public static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
