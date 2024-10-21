package com.sskings.api.gestor.financeiro.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // Testando endpoint público: /api/usuarios/register (POST)
    @Test
    public void testPublicPostEndpoint_withoutAuthentication() throws Exception {
        String userJson = "{\"username\":\"testuser\", \"password\":\"testpassword\", \"email\":\"testuser@email.com\"}";

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)  // Define o tipo de conteúdo como JSON
                        .content(userJson))  // Envia o JSON com os dados necessários
                .andExpect(status().isCreated());
    }

    // Testando endpoint público: /auth/login (POST)
    @Test
    public void testPublicLoginEndpoint_withoutAuthentication() throws Exception {
        String userJson = "{\"username\":\"testuser\", \"password\":\"testpassword\"}";
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isOk());  // Espera-se que seja permitido sem autenticação
    }

    // Testando endpoint protegido: /api/usuarios (GET) sem autenticação
    @Test
    public void testProtectedGetEndpoint_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());  // Espera-se que retorne 403 Forbidden
    }

    // Testando endpoint protegido: /api/usuarios (GET) com autenticação de admin
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testProtectedGetEndpoint_withAdminRole() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk());  // Espera-se que seja permitido com role ADMIN
    }

    // Testando endpoint protegido: /api/lancamentos (GET) sem autenticação
    @Test
    public void testProtectedLancamentosEndpoint_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/lancamentos"))
                .andExpect(status().isForbidden());  // Espera-se que retorne 403 Forbidden
    }

    // Testando endpoint protegido: /api/lancamentos (GET) com autenticação de admin
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testProtectedLancamentosEndpoint_withAdminRole() throws Exception {
        mockMvc.perform(get("/api/lancamentos"))
                .andExpect(status().isOk());  // Espera-se que seja permitido com role ADMIN
    }
}