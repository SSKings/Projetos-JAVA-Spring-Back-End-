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
import com.sskings.ecommerce.model.User;
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
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, com.sskings.ecommerce.config.WebMvcTestConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    @Test
    @DisplayName("POST /api/auth/register — returns 201 with token")
    void register_returns201() throws Exception {
        given(authService.register(any())).willReturn(new AuthResponse("jwt", "Alice", "a@a.com", Role.CUSTOMER, false, false));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Alice", "a@a.com", "secret123", null, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @DisplayName("POST /api/auth/login — valid credentials return 200")
    void login_validCredentials_returns200() throws Exception {
        given(authService.login(any())).willReturn(new AuthResponse("jwt", "Alice", "a@a.com", Role.CUSTOMER, false, false));

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

    @Test
    @DisplayName("POST /api/auth/register — campos acima do tamanho da coluna retornam 400")
    void register_tooLongFields_returns400() throws Exception {
        String body = """
                {"name":"%s","email":"ana@a.com","password":"%s","phone":"%s","acceptTerms":true}"""
                .formatted("A".repeat(101), "p".repeat(73), "1".repeat(21));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                // nome, tamanho da senha, senha sem números, telefone
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    @DisplayName("POST /api/auth/register — senha fraca e termos não aceitos retornam 400 com os motivos")
    void register_weakPasswordAndNoTerms_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana S\",\"email\":\"ana@a.com\",\"password\":\"12345678\",\"acceptTerms\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.containsInAnyOrder(
                        "Password must contain letters and numbers",
                        "You must accept the terms of use and the privacy policy")));
    }

    @Test
    @DisplayName("POST /api/auth/login — conta bloqueada por tentativas retorna 429 com Retry-After")
    void login_locked_returns429() throws Exception {
        given(authService.login(any())).willThrow(
                new com.sskings.ecommerce.exception.TooManyRequestsException("Too many failed attempts. Try again later", 120));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
    }

    @Test
    @DisplayName("POST /api/auth/logout exige login e revoga os tokens")
    void logout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        User ana = User.builder().id(2L).email("ana@a.com").password("h").build();
        mockMvc.perform(post("/api/auth/logout").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(ana)))
                .andExpect(status().isNoContent());
        then(authService).should().logout(2L);
    }
}
