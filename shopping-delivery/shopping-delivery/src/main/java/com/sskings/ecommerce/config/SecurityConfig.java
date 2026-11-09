package com.sskings.ecommerce.config;

import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.RateLimitFilter;
import com.sskings.ecommerce.security.RequestSizeLimitFilter;
import com.sskings.ecommerce.security.SecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

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

    @Value("${spring.h2.console.path:/h2-console}")
    private String h2ConsolePath;

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

    /**
     * Swagger e console H2 (só existem no perfil dev) respondem apenas para quem está na própria máquina.
     * Revisão 3: com a API de dev aberta na rede local (o front é testado de outros aparelhos), qualquer um na
     * mesma Wi-Fi abria o console H2 — que executa SQL arbitrário e, via CREATE ALIAS, código Java no servidor.
     */
    @Bean
    @Order(0)
    @ConditionalOnExpression("${spring.h2.console.enabled:false} or ${springdoc.api-docs.enabled:false}")
    public SecurityFilterChain devToolsFilterChain(HttpSecurity http) throws Exception {
        // O console H2 é um servlet próprio, fora do Spring MVC: casar pelo caminho da URI (um matcher de rota MVC não
        // o reconhece e a requisição caía na cadeia principal).
        List<RequestMatcher> tools = new ArrayList<>();
        if (apiDocsEnabled) {
            PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
            tools.addAll(List.of(path.matcher("/swagger-ui/**"), path.matcher("/swagger-ui.html"), path.matcher("/v3/api-docs/**")));
        }
        if (h2ConsoleEnabled) tools.add(underPath(h2ConsolePath));
        return http
                .securityMatcher(new OrRequestMatcher(tools))
                .csrf(csrf -> csrf.disable())                              // o console H2 posta formulários próprios
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))         // e usa frames
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, ctx) ->
                        new AuthorizationDecision(isLoopback(ctx.getRequest().getRemoteAddr()))))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 403, "Dev tools are only available from localhost"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "Dev tools are only available from localhost")))
                .build();
    }

    static RequestMatcher underPath(String base) {
        String prefix = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return path.equals(prefix) || path.startsWith(prefix + "/");
        };
    }

    static boolean isLoopback(String address) {
        // getRemoteAddr() é sempre um IP literal (não há consulta DNS); vazio/inválido = não é loopback
        if (address == null || address.isBlank() || !address.matches("[0-9a-fA-F:.]+")) return false;
        try {
            return java.net.InetAddress.getByName(address).isLoopbackAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
