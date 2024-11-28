package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.services.ClienteService;
import com.sskings.shopping_delivery.services.EnderecoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

@WebMvcTest
public class EnderecoControllerTest {

    @MockBean
    private EnderecoService enderecoService;

    @MockBean
    private ClienteService clienteService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private EnderecoModel enderecoModel;

    @BeforeEach
    void setUp() {
        ClienteModel clienteModel = new ClienteModel();
        clienteModel.setId(1L);
        clienteModel.setNome("Cliente Teste");
        clienteModel.setEmail("test@email.com");
        clienteModel.setTelefone("123456789");
        clienteModel.setCpf("123.456.789-00");
        clienteModel.setDataCadastro(LocalDateTime.of(2020, 10, 10, 10, 10));

        enderecoModel = new EnderecoModel(
                1L,
                "Rua Test",
                "B12",
                "Bairro Test",
                "Complemento",
                LocalDateTime.now(),
                clienteModel
        );
    }

    @DisplayName("Dado Endereço Deve Salvar E Retornar Endereço")
    @Test
    void dadoEnderecoDeveSalvarERetornarEndereco() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willReturn(enderecoModel.getCliente());
        given(enderecoService.salvar(enderecoModel))
                .willAnswer(invocation -> invocation.getArgument(0));
        // When / Act
        ResultActions response = mockMvc.perform(post("/enderecos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enderecoModel)));

        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.logradouro").value("Rua Test"))
                .andExpect(jsonPath("$.cliente.nome").value("Cliente Teste"));
    }
}
