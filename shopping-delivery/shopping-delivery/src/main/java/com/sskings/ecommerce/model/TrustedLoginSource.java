package com.sskings.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Endereço de onde a conta já entrou com sucesso (ver LoginAttemptService e V7__trusted_login_sources.sql). */
@Entity
@Table(name = "trusted_login_sources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrustedLoginSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** IP (IPv4) ou prefixo /64 (IPv6) — ver LoginAttemptService.sourceKey. */
    @Column(name = "source_key", nullable = false, length = 64)
    private String sourceKey;

    @Column(name = "last_success_at", nullable = false)
    private LocalDateTime lastSuccessAt;
}
