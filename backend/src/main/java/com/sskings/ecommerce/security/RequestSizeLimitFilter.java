package com.sskings.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limita o tamanho do corpo das requisições (revisão 2, R5). A API recebe só JSON pequeno; sem limite, o webhook
 * público lia o corpo inteiro na memória antes de recusar a assinatura (20 MB aceitos), o que permite esgotar a
 * memória com requisições paralelas quando a API está exposta sem o nginx (que limita a 1 MB).
 * - Content-Length acima do limite → 413 sem ler nada.
 * - Sem Content-Length (chunked) → a leitura para no limite e a requisição termina em 413.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** Lançada ao passar do limite durante a leitura; o GlobalExceptionHandler a converte em 413. */
    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(long maxBytes) {
            super("Request body larger than " + maxBytes + " bytes");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(413);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":413,\"message\":\"Request body too large\"}");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            return stream;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        private void count(long n) throws PayloadTooLargeException {
            if (n > 0) read += n;
            if (read > maxBytes) throw new PayloadTooLargeException(maxBytes);
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            count(n);
            return n;
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}
