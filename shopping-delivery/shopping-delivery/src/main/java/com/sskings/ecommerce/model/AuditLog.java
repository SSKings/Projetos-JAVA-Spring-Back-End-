package com.sskings.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Registro imutável de ações relevantes (quem, o quê, em quê, quando, de onde). */
@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(length = 1000)
    private String details;

    @Column(length = 45)
    private String ip;
}
