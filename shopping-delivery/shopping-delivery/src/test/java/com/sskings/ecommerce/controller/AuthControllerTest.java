package com.sskings.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    @Test
    @DisplayName("POST /api/auth/register — returns 201 with token")
    void register_returns201() throws Exception {
        given(authService.register(any())).willReturn(new AuthResponse("jwt", "Alice", "a@a.com", Role.CUSTOMER));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Alice", "a@a.com", "secret123", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @DisplayName("POST /api/auth/login — valid credentials return 200")
    void login_validCredentials_returns200() throws Exception {
        given(authService.login(any())).willReturn(new AuthResponse("jwt", "Alice", "a@a.com", Role.CUSTOMER));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("a@a.com", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    @DisplayName("POST /api/auth/login — wrong credentials return 400")
    void login_badCredentials_returns400() throws Exception {
        given(authService.login(any())).willThrow(new BusinessException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("x@x.com", "wrong"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register — invalid body returns 400")
    void register_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }
}
