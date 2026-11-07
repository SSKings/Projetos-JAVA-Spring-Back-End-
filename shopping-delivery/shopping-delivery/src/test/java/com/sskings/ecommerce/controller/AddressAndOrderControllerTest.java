package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.response.AddressResponse;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.dto.response.OrderItemResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.AddressService;
import com.sskings.ecommerce.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AddressController.class, OrderController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class AddressAndOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AddressService addressService;
    @MockBean OrderService orderService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private static final User ANA = User.builder().id(2L).name("Ana").email("ana@a.com").password("h").build();
    private static final AddressResponse ADDRESS =
            new AddressResponse(7L, "01000-000", "Rua A", "1", null, "Centro", "SP", "SP");
    private static final OrderResponse ORDER =
            new OrderResponse(3L, OrderStatus.PENDING, BigDecimal.TEN, LocalDateTime.now(), ADDRESS,
                    List.of(new OrderItemResponse(1L, 10L, "Caneca", 2, new BigDecimal("5.00"), BigDecimal.TEN)), null,
                    null, null, null, true, false);

    @Test
    @DisplayName("GET /api/addresses lista endereços do usuário logado")
    void listAddresses() throws Exception {
        given(addressService.findAllByUser(2L)).willReturn(List.of(ADDRESS));

        mockMvc.perform(get("/api/addresses").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].street").value("Rua A"));
    }

    @Test
    @DisplayName("POST /api/addresses cria endereço (201)")
    void createAddress() throws Exception {
        given(addressService.create(any(), eq(2L))).willReturn(ADDRESS);

        mockMvc.perform(post("/api/addresses").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01000-000","street":"Rua A","number":"1",
                                 "neighborhood":"Centro","city":"SP","state":"SP"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST /api/addresses com UF inválida retorna 400")
    void createAddress_invalidState_returns400() throws Exception {
        mockMvc.perform(post("/api/addresses").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01000-000","street":"Rua A","neighborhood":"Centro","city":"SP","state":"SPX"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/addresses com campo maior que a coluna do banco retorna 400 (e não 500)")
    void createAddress_tooLongField_returns400() throws Exception {
        String street = "R".repeat(151);
        mockMvc.perform(post("/api/addresses").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01000-000","street":"%s","neighborhood":"Centro","city":"SP","state":"SP"}"""
                                .formatted(street)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Street must have at most 150 characters"));
    }

    @Test
    @DisplayName("DELETE /api/addresses/{id} de endereço usado em pedido retorna 400")
    void deleteAddress_linkedToOrder_returns400() throws Exception {
        willThrow(new BusinessException("Address is linked to an order")).given(addressService).delete(7L, 2L);

        mockMvc.perform(delete("/api/addresses/7").with(user(ANA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Address is linked to an order"));
    }

    @Test
    @DisplayName("DELETE /api/addresses/{id} (204)")
    void deleteAddress() throws Exception {
        mockMvc.perform(delete("/api/addresses/7").with(user(ANA)))
                .andExpect(status().isNoContent());
        verify(addressService).delete(7L, 2L);
    }

    @Test
    @DisplayName("POST /api/orders/checkout cria pedido (201)")
    void checkout() throws Exception {
        given(orderService.checkout(any(), eq(2L))).willReturn(ORDER);

        mockMvc.perform(post("/api/orders/checkout").with(user(ANA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders e /api/orders/{id}")
    void listAndGetOrders() throws Exception {
        given(orderService.findAllByUser(2L)).willReturn(List.of(ORDER));
        given(orderService.findById(3L, 2L)).willReturn(ORDER);

        mockMvc.perform(get("/api/orders").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].items[0].productId").value(10))
                .andExpect(jsonPath("$[0].items[0].productName").value("Caneca"));
        mockMvc.perform(get("/api/orders/3").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
    }

    @Test
    @DisplayName("Pedido de outro usuário retorna 404")
    void getOrder_notOwned_returns404() throws Exception {
        given(orderService.findById(9L, 2L)).willThrow(new ResourceNotFoundException("Order not found: 9"));

        mockMvc.perform(get("/api/orders/9").with(user(ANA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: 9"));
    }

    @Test
    @DisplayName("Erro inesperado vira 500 sem vazar detalhes")
    void unexpectedError_returns500() throws Exception {
        given(orderService.findAllByUser(2L)).willThrow(new IllegalStateException("db down"));

        mockMvc.perform(get("/api/orders").with(user(ANA)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel e /return (motivo opcional, até 500 caracteres)")
    void cancelAndReturn() throws Exception {
        given(orderService.cancelByCustomer(3L, 2L)).willReturn(ORDER);
        given(orderService.requestReturn(eq(3L), eq(2L), any())).willReturn(ORDER);

        mockMvc.perform(post("/api/orders/3/cancel").with(user(ANA))).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/3/return").with(user(ANA))).andExpect(status().isOk());
        verify(orderService).requestReturn(3L, 2L, null);
        mockMvc.perform(post("/api/orders/3/return").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"não serviu\"}"))
                .andExpect(status().isOk());
        verify(orderService).requestReturn(3L, 2L, "não serviu");
        mockMvc.perform(post("/api/orders/3/return").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Conflito de lock otimista vira 409")
    void optimisticLock_returns409() throws Exception {
        given(orderService.cancelByCustomer(3L, 2L)).willThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(com.sskings.ecommerce.model.Order.class, 3L));
        mockMvc.perform(post("/api/orders/3/cancel").with(user(ANA))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Restrição do banco violada por requisição simultânea → 409 sem detalhes do SQL (antes: 500)")
    void dataIntegrityViolation_returns409() throws Exception {
        given(orderService.cancelByCustomer(3L, 2L)).willThrow(new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement [Unique index violation: CART_ITEMS(USER_ID, PRODUCT_ID)]"));
        mockMvc.perform(post("/api/orders/3/cancel").with(user(ANA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data. Reload and try again"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CART_ITEMS"))));
    }

    @Test
    @DisplayName("Transação vítima de deadlock → 409 (pode repetir), sem detalhes do SQL (antes: 500)")
    void deadlockVictim_returns409() throws Exception {
        given(orderService.cancelByCustomer(3L, 2L)).willThrow(new org.springframework.dao.CannotAcquireLockException(
                "update products set stock_quantity=... [ERROR: deadlock detected]"));
        mockMvc.perform(post("/api/orders/3/cancel").with(user(ANA)))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("deadlock"))));
    }
}
