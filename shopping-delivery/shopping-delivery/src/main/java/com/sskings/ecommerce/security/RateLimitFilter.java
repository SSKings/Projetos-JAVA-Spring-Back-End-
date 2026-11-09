package com.sskings.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite por IP em janela fixa de 1 minuto para rotas sensíveis (login, cadastro, webhook Pix).
 * Em memória: vale por instância. Com várias réplicas, aplique também no proxy/gateway (nginx limit_req)
 * ou troque por um store compartilhado (Redis). O IP vem de request.getRemoteAddr(), que respeita
 * X-Forwarded-For só quando server.forward-headers-strategy está ativo (perfil prod, atrás do proxy).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int authPerMinute;
    private final int webhookPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastSweepMinute = -1;

    public RateLimitFilter(SecurityProperties.RateLimit props, Clock clock) {
        this.enabled = props.enabled();
        this.authPerMinute = props.authPerMinute();
        this.webhookPerMinute = props.webhookPerMinute();
        this.clock = clock;
    }

    private record Window(long minute, AtomicInteger count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equals(request.getMethod()) || bucketOf(request.getRequestURI()) == null;
    }

    static String bucketOf(String uri) {
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")) return "auth";
        if (uri.equals("/api/payments/pix/webhook")) return "webhook";
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = bucketOf(request.getRequestURI());
        int limit = bucket.equals("auth") ? authPerMinute : webhookPerMinute;
        long nowSeconds = clock.instant().getEpochSecond();
        long minute = nowSeconds / 60;
        // IPv6 por bloco /64: o cliente troca de endereço dentro do bloco à vontade (revisão 4, R4-1)
        String key = bucket + ":" + com.sskings.ecommerce.service.LoginAttemptService.sourceKey(request.getRemoteAddr());

        Window window = windows.compute(key, (k, w) -> w == null || w.minute() != minute ? new Window(minute, new AtomicInteger()) : w);
        if (window.count().incrementAndGet() > limit) {
            long retryAfter = 60 - (nowSeconds % 60);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
            return;
        }
        // evita crescer sem limite; uma varredura por minuto (cheio de janelas do minuto atual, varrer a cada
        // requisição só gastava CPU — revisão 4)
        if (windows.size() > 10_000 && lastSweepMinute != minute) {
            lastSweepMinute = minute;
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        chain.doFilter(request, response);
    }
}
