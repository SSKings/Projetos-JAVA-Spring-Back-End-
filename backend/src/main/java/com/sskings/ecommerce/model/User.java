package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Incrementado em logout, troca de papel, bloqueio e MFA: invalida todos os JWT já emitidos. */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    /** Segredo TOTP cifrado com AES-GCM (CryptoService); nunca em texto puro. */
    @Column(name = "mfa_secret")
    private String mfaSecret;

    /** Último passo TOTP aceito: impede reutilizar o mesmo código. */
    @Column(name = "mfa_last_step")
    private Long mfaLastStep;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /** Quando o titular confirmou o e-mail pelo link (ADR-025). Sem isso não fecha pedido nem recebe avisos. */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** Preenchido quando o titular exclui a conta (LGPD art. 18, VI): dados pessoais anonimizados. */
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    public void revokeTokens() {
        tokenVersion++;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getUsername()              { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return active; }
}
