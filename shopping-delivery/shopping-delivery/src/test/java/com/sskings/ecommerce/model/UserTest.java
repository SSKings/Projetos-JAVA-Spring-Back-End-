package com.sskings.ecommerce.model;

import com.sskings.ecommerce.model.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("Novo usuário é CUSTOMER ativo por padrão")
    void defaults() {
        User user = User.builder().email("a@a.com").build();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getUsername()).isEqualTo("a@a.com");
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("Admin recebe ROLE_ADMIN; bloqueado fica desabilitado")
    void adminAndBlocked() {
        User user = User.builder().role(Role.ADMIN).active(false).build();
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.isAccountNonLocked()).isFalse();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }
}
