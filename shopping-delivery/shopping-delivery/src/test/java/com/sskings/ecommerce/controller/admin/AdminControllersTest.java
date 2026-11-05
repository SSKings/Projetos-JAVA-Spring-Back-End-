package com.sskings.ecommerce.controller.admin;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.dto.response.*;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.admin.AdminDashboardService;
import com.sskings.ecommerce.service.admin.AdminOrderService;
import com.sskings.ecommerce.service.admin.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminOrderController.class, AdminUserController.class, AdminDashboardController.class, AdminAuditController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class AdminControllersTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminOrderService orderService;
    @MockBean AdminUserService userService;
    @MockBean AdminDashboardService dashboardService;
    @MockBean com.sskings.ecommerce.service.AuditService auditService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private static final User ADMIN =
            User.builder().id(1L).name("Admin").email("admin@x.com").password("h").role(Role.ADMIN).build();

    private static final AddressResponse ADDRESS =
            new AddressResponse(1L, "01000-000", "Rua A", "1", null, "Centro", "SP", "SP");

    private AdminOrderResponse order(OrderStatus status) {
        return new AdminOrderResponse(5L, status, BigDecimal.TEN, LocalDateTime.now(), 2L, "Ana", "ana@a.com", ADDRESS, List.of(),
                status.nextStatuses(), null, null, null, null);
    }

    // ---------- Autenticação real via JwtAuthFilter ----------

    @Test
    @DisplayName("Token JWT válido de admin acessa o dashboard")
    void dashboard_withAdminJwt_returns200() throws Exception {
        given(jwtService.validateToken("tok")).willReturn(new JwtService.TokenClaims("admin@x.com", 0));
        given(userRepository.findByEmail("admin@x.com")).willReturn(Optional.of(ADMIN));
        given(dashboardService.getStats()).willReturn(new DashboardResponse(10, 9, 1, 30, 40, 2, new BigDecimal("100.00")));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(30))
                .andExpect(jsonPath("$.revenue").value(100.00));
    }

    @Test
    @DisplayName("Token JWT de cliente recebe 403 nas rotas de admin")
    void dashboard_withCustomerJwt_returns403() throws Exception {
        User customer = User.builder().id(2L).email("ana@a.com").role(Role.CUSTOMER).build();
        given(jwtService.validateToken("tok")).willReturn(new JwtService.TokenClaims("ana@a.com", 0));
        given(userRepository.findByEmail("ana@a.com")).willReturn(Optional.of(customer));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer tok"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Token de usuário bloqueado não autentica")
    void dashboard_withBlockedUserJwt_returns403() throws Exception {
        User blocked = User.builder().id(1L).email("admin@x.com").role(Role.ADMIN).active(false).build();
        given(jwtService.validateToken("tok")).willReturn(new JwtService.TokenClaims("admin@x.com", 0));
        given(userRepository.findByEmail("admin@x.com")).willReturn(Optional.of(blocked));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer tok"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token inválido não autentica")
    void dashboard_withInvalidJwt_returns401() throws Exception {
        given(jwtService.validateToken("bad")).willReturn(null);

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @DisplayName("Token revogado (versão antiga, ex.: após logout) não autentica")
    void dashboard_withRevokedJwt_returns401() throws Exception {
        User admin = User.builder().id(1L).email("admin@x.com").password("h").role(Role.ADMIN).tokenVersion(3).build();
        given(jwtService.validateToken("old")).willReturn(new JwtService.TokenClaims("admin@x.com", 2));
        given(userRepository.findByEmail("admin@x.com")).willReturn(Optional.of(admin));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer old"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Pedidos ----------

    @Test
    @DisplayName("GET /api/admin/orders filtra por status")
    void listOrders_byStatus() throws Exception {
        given(orderService.findAll(eq(OrderStatus.PENDING), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(order(OrderStatus.PENDING))));

        mockMvc.perform(get("/api/admin/orders").param("status", "PENDING").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerEmail").value("ana@a.com"));
    }

    @Test
    @DisplayName("GET /api/admin/orders com status inexistente retorna 400")
    void listOrders_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/orders").param("status", "FOO").with(user(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/admin/orders/{id}")
    void getOrder() throws Exception {
        given(orderService.findById(5L)).willReturn(order(OrderStatus.SHIPPED));

        mockMvc.perform(get("/api/admin/orders/5").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{id}/status altera o status")
    void updateOrderStatus() throws Exception {
        given(orderService.updateStatus(5L, OrderStatus.CONFIRMED)).willReturn(order(OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/admin/orders/5/status").with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.nextStatuses[0]").value("SHIPPED"))
                .andExpect(jsonPath("$.nextStatuses.length()").value(3));
    }

    @Test
    @DisplayName("PATCH status sem corpo válido retorna 400")
    void updateOrderStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/5/status").with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Status is required"));
    }

    // ---------- Usuários ----------

    private UserResponse ana(boolean active) {
        return new UserResponse(2L, "Ana", "ana@a.com", null, Role.CUSTOMER, active, LocalDateTime.now(), 3);
    }

    @Test
    @DisplayName("GET /api/admin/users busca por nome/email")
    void listUsers() throws Exception {
        given(userService.findAll(eq("ana"), any(Pageable.class))).willReturn(new PageImpl<>(List.of(ana(true))));

        mockMvc.perform(get("/api/admin/users").param("search", "ana").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderCount").value(3));
    }

    @Test
    @DisplayName("GET /api/admin/users/{id} e /orders")
    void getUserAndOrders() throws Exception {
        given(userService.findById(2L)).willReturn(ana(true));
        given(userService.findOrders(2L)).willReturn(List.of());

        mockMvc.perform(get("/api/admin/users/2").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@a.com"));
        mockMvc.perform(get("/api/admin/users/2/orders").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{id} repassa o id do admin logado")
    void updateUser_passesCurrentAdminId() throws Exception {
        given(userService.update(eq(2L), eq(new UpdateUserRequest(false, null)), eq(1L))).willReturn(ana(false));

        mockMvc.perform(patch("/api/admin/users/2").with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("Regra de negócio violada vira 400 com mensagem")
    void updateUser_self_returns400() throws Exception {
        given(userService.update(eq(1L), any(), eq(1L)))
                .willThrow(new BusinessException("You cannot change your own account"));

        mockMvc.perform(patch("/api/admin/users/1").with(user(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot change your own account"));
    }

    // ---------- Auditoria e estorno ----------

    @Test
    @DisplayName("GET /api/admin/audit lista a trilha (filtro por ação); cliente recebe 403")
    void audit() throws Exception {
        var log = com.sskings.ecommerce.model.AuditLog.builder().id(1L).action("LOGIN_FAILED").actorEmail("a@a.com")
                .ip("1.2.3.4").createdAt(LocalDateTime.now()).build();
        given(auditService.search(eq("LOGIN_FAILED"), any())).willReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get("/api/admin/audit").param("action", "LOGIN_FAILED").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.content[0].ip").value("1.2.3.4"));
        mockMvc.perform(get("/api/admin/audit").with(user(User.builder().id(2L).email("c@c.com").password("h").role(Role.CUSTOMER).build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("POST /api/admin/orders/{id}/payment/refund registra o estorno")
    void refund() throws Exception {
        given(orderService.refundPayment(5L)).willReturn(order(OrderStatus.CANCELLED));
        mockMvc.perform(post("/api/admin/orders/5/payment/refund").with(user(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
