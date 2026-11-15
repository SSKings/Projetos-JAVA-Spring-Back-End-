package com.sskings.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.request.AddCartItemRequest;
import com.sskings.ecommerce.dto.request.UpdateCartItemRequest;
import com.sskings.ecommerce.dto.response.CartSummaryResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class CartControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CartService cartService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private User mockUser() {
        return User.builder().id(1L).name("Alice").email("a@a.com").password("hashed").build();
    }

    private CartSummaryResponse emptySummary() {
        return new CartSummaryResponse(List.of(), BigDecimal.ZERO, 0);
    }

    @Test
    @DisplayName("GET /api/cart — authenticated user gets cart")
    void getCart_authenticated_returns200() throws Exception {
        given(cartService.getCart(1L)).willReturn(emptySummary());

        mockMvc.perform(get("/api/cart").with(user(mockUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/cart — unauthenticated returns 401 (front-end faz logout só em 401)")
    void getCart_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/cart/items — adds item and returns updated cart")
    void addItem_authenticated_returns200() throws Exception {
        CartSummaryResponse summary = new CartSummaryResponse(List.of(), new BigDecimal("20.00"), 1);
        given(cartService.addItem(any(), anyLong())).willReturn(summary);

        mockMvc.perform(post("/api/cart/items")
                        .with(user(mockUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(10L, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.itemCount").value(1));
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{id} — removes item and returns updated cart")
    void removeItem_authenticated_returns200() throws Exception {
        given(cartService.removeItem(anyLong(), anyLong())).willReturn(emptySummary());

        mockMvc.perform(delete("/api/cart/items/1").with(user(mockUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("PUT /api/cart/items/{id} — updates quantity")
    void updateItem_authenticated_returns200() throws Exception {
        CartSummaryResponse summary = new CartSummaryResponse(List.of(), new BigDecimal("30.00"), 1);
        given(cartService.updateItem(anyLong(), any(), anyLong())).willReturn(summary);

        mockMvc.perform(put("/api/cart/items/1")
                        .with(user(mockUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30.00));
    }
}
