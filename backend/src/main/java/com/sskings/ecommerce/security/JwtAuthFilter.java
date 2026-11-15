package com.sskings.ecommerce.security;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Admin que ainda não ativou o MFA: autenticado, mas sem ROLE_ADMIN. */
    public static final String MFA_SETUP_REQUIRED = "MFA_SETUP_REQUIRED";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final boolean requireAdminMfa;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository,
                         @Value("${security.require-admin-mfa:false}") boolean requireAdminMfa) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.requireAdminMfa = requireAdminMfa;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            JwtService.TokenClaims claims = jwtService.validateToken(header.substring(7));
            if (claims != null) {
                userRepository.findByEmail(claims.email())
                        .filter(User::isEnabled)
                        .filter(user -> user.getTokenVersion() == claims.version())   // revogação
                        .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, authoritiesOf(user))));
            }
        }
        chain.doFilter(request, response);
    }

    Collection<? extends GrantedAuthority> authoritiesOf(User user) {
        if (requireAdminMfa && user.getRole() == Role.ADMIN && !user.isMfaEnabled()) {
            return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority(MFA_SETUP_REQUIRED));
        }
        return user.getAuthorities();
    }
}
