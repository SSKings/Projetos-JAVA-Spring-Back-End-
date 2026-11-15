package com.sskings.ecommerce.controller.admin;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.response.AdminProductResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.admin.AdminProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProductController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class AdminProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminProductService service;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private static User userWithRole(Role role) {
        return User.builder().id(1L).name("X").email("x@x.com").password("h").role(role).build();
    }

    private static final String VALID_BODY = """
            {"name":"Caneca","description":"Cerâmica","price":29.90,"stockQuantity":10}
            """;

    @Test
    @DisplayName("POST /api/admin/products — sem autenticação retorna 401")
    void create_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/admin/products — cliente comum retorna 403")
    void create_customer_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/products").with(user(userWithRole(Role.CUSTOMER)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/products — admin cria produto e recebe 201")
    void create_admin_returns201() throws Exception {
        given(service.create(any())).willReturn(
                new AdminProductResponse(11L, "Caneca", "Cerâmica", null, new BigDecimal("29.90"), 10, true));

        mockMvc.perform(post("/api/admin/products").with(user(userWithRole(Role.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/products — preço inválido retorna 400")
    void create_invalidPrice_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/products").with(user(userWithRole(Role.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Caneca\",\"price\":0,\"stockQuantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/admin/products/{id} — admin desativa produto (204)")
    void deactivate_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/products/1").with(user(userWithRole(Role.ADMIN))))
                .andExpect(status().isNoContent());
    }
}
