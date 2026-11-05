package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorEmail, String action,
                               String targetType, String targetId, String details, String ip) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActorId(), a.getActorEmail(), a.getAction(),
                a.getTargetType(), a.getTargetId(), a.getDetails(), a.getIp());
    }
}
