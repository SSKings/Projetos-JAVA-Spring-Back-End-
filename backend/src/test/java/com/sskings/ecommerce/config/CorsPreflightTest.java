package com.sskings.ecommerce.config;

import com.sskings.ecommerce.controller.CartController;
import com.sskings.ecommerce.controller.ProductController;
import com.sskings.ecommerce.controller.admin.AdminOrderController;
import com.sskings.ecommerce.controller.admin.AdminUserController;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.CartService;
import com.sskings.ecommerce.service.ProductService;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.service.admin.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O navegador envia um preflight OPTIONS antes de qualquer requisição com JSON ou com método diferente de
 * GET/POST. Os demais testes de controller não passam por ele — foi assim que a falta de PATCH no CORS
 * (status do pedido / bloqueio de usuário no admin) chegou ao front-end como "não foi possível conectar".
 */
@WebMvcTest({AdminOrderController.class, AdminUserController.class, CartController.class, ProductController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class CorsPreflightTest {

    private static final String FRONT = "http://localhost:5173";

    @Autowired MockMvc mockMvc;
    @MockBean AdminOrderService adminOrderService;
    @MockBean AdminUserService adminUserService;
    @MockBean CartService cartService;
    @MockBean ProductService productService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    @ParameterizedTest(name = "preflight {0} {1}")
    @CsvSource({
            "PATCH,  /api/admin/orders/1/status",
            "PATCH,  /api/admin/users/2",
            "GET,    /api/admin/orders",
            "PUT,    /api/cart/items/1",
            "DELETE, /api/cart/items/1",
            "POST,   /api/cart/items",
            "GET,    /api/products",
    })
    void preflightIsAccepted(String method, String url) throws Exception {
        mockMvc.perform(options(url)
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
    }

    @Test
    @DisplayName("Preflight aceita os cabeçalhos W3C Trace Context enviados pela instrumentação do front (ADR-022)")
    void preflight_acceptsTraceContextHeaders() throws Exception {
        mockMvc.perform(options("/api/cart/items")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,traceparent,tracestate"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("traceparent")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("tracestate")));
    }

    @Test
    @DisplayName("Cabeçalho fora da lista continua bloqueado no preflight")
    void preflight_unknownHeader_isRejected() throws Exception {
        mockMvc.perform(options("/api/cart/items")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-forwarded-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Método fora da lista continua bloqueado no preflight")
    void preflight_unknownMethod_isRejected() throws Exception {
        mockMvc.perform(options("/api/admin/orders/1/status")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A lista de métodos inclui todos os verbos usados pelo front-end")
    void allowedMethods_coverFrontend() {
        assertThat(CorsConfig.ALLOWED_METHODS).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
