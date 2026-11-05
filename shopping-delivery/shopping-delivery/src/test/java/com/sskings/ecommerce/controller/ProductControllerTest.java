package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.response.ProductResponse;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.ProductService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ProductService productService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    @Test
    @DisplayName("GET /api/products — public endpoint returns 200 without auth")
    void listProducts_noAuth_returns200() throws Exception {
        ProductResponse pr = new ProductResponse(1L, "Widget", "desc", null, BigDecimal.TEN, 5);
        given(productService.findAll(isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(pr)));

        mockMvc.perform(get("/api/products").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Widget"))
                .andExpect(jsonPath("$.content[0].price").value(10));
    }

    @Test
    @DisplayName("GET /api/products/{id} — public, returns single product")
    void getProductById_noAuth_returns200() throws Exception {
        ProductResponse pr = new ProductResponse(1L, "Widget", "desc", null, BigDecimal.TEN, 5);
        given(productService.findById(1L)).willReturn(pr);

        mockMvc.perform(get("/api/products/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/cart — protected, returns 403 without auth")
    void getCart_noAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/cart").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
