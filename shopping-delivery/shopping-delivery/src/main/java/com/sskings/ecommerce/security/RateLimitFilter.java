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
        String key = bucket + ":" + request.getRemoteAddr();

        Window window = windows.compute(key, (k, w) -> w == null || w.minute() != minute ? new Window(minute, new AtomicInteger()) : w);
        if (window.count().incrementAndGet() > limit) {
            long retryAfter = 60 - (nowSeconds % 60);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);   // evita crescer sem limite
        }
        chain.doFilter(request, response);
    }
}
