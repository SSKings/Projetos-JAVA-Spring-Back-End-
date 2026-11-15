package com.sskings.ecommerce.security;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthFilterTest {

    private static java.util.List<String> roles(JwtAuthFilter f, User u) {
        return f.authoritiesOf(u).stream().map(GrantedAuthority::getAuthority).map(String::valueOf).toList();
    }

    @Test
    @DisplayName("Com MFA obrigatório, admin sem MFA perde ROLE_ADMIN até ativar; com MFA ativo mantém")
    void adminMfaRequired() {
        JwtAuthFilter required = new JwtAuthFilter(mock(JwtService.class), mock(UserRepository.class), true);
        User admin = User.builder().role(Role.ADMIN).build();
        assertThat(roles(required, admin)).containsExactly("ROLE_CUSTOMER", JwtAuthFilter.MFA_SETUP_REQUIRED);

        admin.setMfaEnabled(true);
        assertThat(roles(required, admin)).containsExactly("ROLE_ADMIN");
        assertThat(roles(required, User.builder().role(Role.CUSTOMER).build())).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("Com MFA opcional (dev), admin mantém ROLE_ADMIN")
    void adminMfaOptional() {
        JwtAuthFilter optional = new JwtAuthFilter(mock(JwtService.class), mock(UserRepository.class), false);
        assertThat(roles(optional, User.builder().role(Role.ADMIN).build())).containsExactly("ROLE_ADMIN");
    }
}
