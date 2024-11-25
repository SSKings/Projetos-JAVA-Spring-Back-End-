package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.EmailExistenteException;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.services.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
public class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClienteService clienteService;

    private ClienteModel clienteModel;

    @BeforeEach
    void setUp() {
        // Configura um cliente com endereços e pedidos
        clienteModel = new ClienteModel();
        clienteModel.setId(1L);
        clienteModel.setNome("Cliente Teste");
        clienteModel.setEmail("test@email.com");
        clienteModel.setTelefone("123456789");
        clienteModel.setCpf("123.456.789-00");
        clienteModel.setDataCadastro(LocalDateTime.of(2020, 10, 10, 10, 10));
    }

    @DisplayName("Dado Cliente Deve Salvar E Retornar O Cliente")
    @Test
    void dadoClienteDeveSalvarERetornarOCliente() throws Exception {
        // Given / Arrange
        given(clienteService.salvar(any(ClienteModel.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));
        // When / Act
        ResultActions response = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteModel)));

        // Then / Assert

        response.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(clienteModel.getNome()))
                .andExpect(jsonPath("$.email").value(clienteModel.getEmail()));

    }

    @DisplayName("Dado Cliente Quando Salvar Deve Lançar Excecão Quando Email Já Existir")
    @Test
    void dadoClienteQuandoSalvarDeveLancarExcecaoQuandoEmailJaExistir() throws Exception {
        // Given / Arrange
        given(clienteService.salvar(any(ClienteModel.class)))
                .willThrow(EmailExistenteException.class);
        // When / Act
        ResultActions response = mockMvc.perform(post("/clientes"));

        // Then / Assert
        response.andExpect(status().isBadRequest())
                .andDo(print());
    }

    // test[System Under Test]_[Condition or State Change]_[Expected Result]
    @DisplayName("Dado Uma Lista De Clientes Quando Listar Então Retorne Uma Lista de Clientes")
    @Test
    void dadoUmaListaDeClientesQuandoListarEntaoRetorneUmaListaDeClientes() throws Exception {
        // Given / Arrange
        List<ClienteModel> clientes = new ArrayList<>();
        ClienteModel cliente2 = new ClienteModel(2L, "Vera", "Email", "71-1111-1111", "060-606-906-60", LocalDateTime.now(), null, null );
        clientes.addAll(List.of(clienteModel, cliente2));
        given(clienteService.listar()).willReturn(clientes);
        // When / Act
        ResultActions response = mockMvc.perform(get("/clientes"));
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.size()").value(clientes.size()));
    }




}
