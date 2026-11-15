package com.sskings.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);

    @Test
    @DisplayName("Content-Length acima do limite → 413 sem chegar ao controller")
    void contentLengthTooLarge() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/payments/pix/webhook");
        req.setContent(new byte[11]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(res.getContentAsString()).contains("Request body too large");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Corpo dentro do limite passa inteiro, leitura byte a byte ou em bloco")
    void withinLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContent("0123456789".getBytes());
        AtomicReference<String> body = new AtomicReference<>();
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
            var in = ((HttpServletRequest) r).getInputStream();
            int first = in.read();
            body.set((char) first + new String(in.readAllBytes()));
            assertThat(in.read()).isEqualTo(-1);
            assertThat(((HttpServletRequest) r).getInputStream()).isSameAs(in);
        });
        assertThat(body.get()).isEqualTo("0123456789");
    }

    @Test
    @DisplayName("Sem Content-Length (chunked): a leitura para no limite com PayloadTooLargeException")
    void chunkedOverLimit() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/register") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        req.setContent(new byte[50]);
        assertThatThrownBy(() -> filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
            var in = ((HttpServletRequest) r).getInputStream();
            assertThat(in.isFinished()).isFalse();
            assertThat(in.isReady()).isTrue();
            in.readAllBytes();
        })).isInstanceOf(RequestSizeLimitFilter.PayloadTooLargeException.class)
           .isInstanceOf(IOException.class)
           .hasMessageContaining("10 bytes");
    }
}
