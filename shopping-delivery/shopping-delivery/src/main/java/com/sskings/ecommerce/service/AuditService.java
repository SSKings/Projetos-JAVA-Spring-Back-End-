package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.AuditLog;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Trilha de auditoria.
 * O ator, quando não informado, é o usuário autenticado da requisição (null = sistema/PSP).
 * - record(): na mesma transação da ação (se a ação falhar e der rollback, o registro some junto).
 * - recordIndependent(): em transação própria, para eventos de segurança que acompanham uma falha
 *   (login recusado, webhook rejeitado) e precisam ficar gravados mesmo com o rollback da ação.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
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

    private final AuditLogRepository repository;
    private final Clock clock;

    @Transactional
    public void record(User actor, String action, String targetType, Object targetId, String details) {
        save(actor, action, targetType, targetId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(User actor, String action, String targetType, Object targetId, String details) {
        save(actor, action, targetType, targetId, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, Pageable pageable) {
        return action == null || action.isBlank()
                ? repository.findAll(pageable)
                : repository.findAllByAction(action, pageable);
    }

    private void save(User explicitActor, String action, String targetType, Object targetId, String details) {
        User actor = explicitActor != null ? explicitActor : currentUser();
        repository.save(AuditLog.builder()
                .createdAt(LocalDateTime.now(clock))
                .actorId(actor != null ? actor.getId() : null)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .action(action)
                .targetType(targetType)
                .targetId(targetId != null ? String.valueOf(targetId) : null)
                .details(details != null && details.length() > 1000 ? details.substring(0, 1000) : details)
                .ip(currentIp())
                .build());
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
