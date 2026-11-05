package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.config.SecurityConfig;
import com.sskings.ecommerce.config.WebMvcTestConfig;
import com.sskings.ecommerce.dto.request.PasswordConfirmationRequest;
import com.sskings.ecommerce.dto.request.UpdateProfileRequest;
import com.sskings.ecommerce.dto.response.*;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtAuthFilter;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, WebMvcTestConfig.class})
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AccountService accountService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;

    private static final User ANA = User.builder().id(2L).name("Ana").email("ana@a.com").password("h").build();
    private static final ProfileResponse PROFILE =
            new ProfileResponse(2L, "Ana", "ana@a.com", null, Role.CUSTOMER, LocalDateTime.now(), false, "v1", LocalDateTime.now());

    @Test
    @DisplayName("Todas as rotas de conta exigem login")
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/data-export")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/delete")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Perfil: leitura e atualização com validação")
    void profile() throws Exception {
        given(accountService.profile(2L)).willReturn(PROFILE);
        given(accountService.updateProfile(eq(2L), any(UpdateProfileRequest.class))).willReturn(PROFILE);

        mvc.perform(get("/api/account").with(user(ANA))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@a.com"));
        mvc.perform(put("/api/account").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/account").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Exportação vem como anexo JSON")
    void export() throws Exception {
        given(accountService.exportData(2L)).willReturn(new DataExportResponse(LocalDateTime.now(), PROFILE, List.of(), List.of(), List.of()));
        mvc.perform(get("/api/account/data-export").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"meus-dados-shopeasy.json\""))
                .andExpect(jsonPath("$.profile.email").value("ana@a.com"));
    }

    @Test
    @DisplayName("Exclusão: 204; senha errada → 400; corpo sem senha → 400")
    void delete() throws Exception {
        mvc.perform(post("/api/account/delete").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"senha123\"}"))
                .andExpect(status().isNoContent());
        then(accountService).should().deleteAccount(2L, new PasswordConfirmationRequest("senha123", null));

        willThrow(new BusinessException("Invalid password")).given(accountService).deleteAccount(eq(2L), eq(new PasswordConfirmationRequest("x", null)));
        mvc.perform(post("/api/account/delete").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid password"));
        mvc.perform(post("/api/account/delete").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MFA: setup, enable (código de 6 dígitos) e disable")
    void mfa() throws Exception {
        AuthResponse auth = new AuthResponse("new-token", "Ana", "ana@a.com", Role.CUSTOMER, true, false);
        given(accountService.setupMfa(2L)).willReturn(new MfaSetupResponse("ABC", "otpauth://totp/x"));
        given(accountService.enableMfa(2L, "123456")).willReturn(auth);
        given(accountService.disableMfa(eq(2L), any())).willReturn(auth);

        mvc.perform(post("/api/account/mfa/setup").with(user(ANA))).andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/x"));
        mvc.perform(post("/api/account/mfa/enable").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(jsonPath("$.token").value("new-token"));
        mvc.perform(post("/api/account/mfa/enable").with(user(ANA)).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/mfa/disable").with(user(ANA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha123\",\"mfaCode\":\"123456\"}"))
                .andExpect(status().isOk());
    }
}
