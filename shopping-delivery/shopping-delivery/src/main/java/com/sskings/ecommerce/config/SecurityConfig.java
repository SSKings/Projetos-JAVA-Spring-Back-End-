package com.sskings.ecommerce.config;

import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.RateLimitFilter;
import com.sskings.ecommerce.security.RequestSizeLimitFilter;
import com.sskings.ecommerce.security.SecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    /** Tamanho máximo do corpo das requisições (a API só recebe JSON pequeno). */
    @Value("${security.max-request-bytes:65536}")
    private long maxRequestBytes;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // API stateless com Bearer token (nada de cookie de sessão): CSRF não se aplica
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .exceptionHandling(e -> e
                        // 401 = sem login/token inválido (o front faz logout); 403 = logado mas sem permissão
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        // PSP não envia JWT; o webhook é autenticado pela assinatura HMAC (PaymentService)
                        .requestMatchers(HttpMethod.POST, "/api/payments/pix/webhook").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Observabilidade (ADR-022): health é público e sem detalhes; métricas só para ADMIN
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new RequestSizeLimitFilter(maxRequestBytes), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(securityProperties.rateLimit(), clock), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void writeError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Swagger e console H2 só ficam fora da cadeia quando estão habilitados (perfil dev). */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        List<String> ignored = new ArrayList<>();
        if (apiDocsEnabled) ignored.addAll(List.of("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"));
        if (h2ConsoleEnabled) ignored.add("/h2-console/**");
        return web -> {
            if (!ignored.isEmpty()) web.ignoring().requestMatchers(ignored.toArray(String[]::new));
        };
    }
}
