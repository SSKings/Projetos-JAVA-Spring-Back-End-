package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.ClienteNaoEncontradoException;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
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
        ResultActions response = mockMvc.perform(post("/clientes", clienteModel)
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

    @DisplayName("Dado Um ClienteId Quando BuscarPorId Deve Retornar Um Cliente")
    @Test
    void dadoUmClienteIdQuandoBuscarPorIdDeveRetornarUmCliente() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willReturn(clienteModel);
        // When / Act
        ResultActions response = mockMvc.perform(get("/clientes/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.nome").value(clienteModel.getNome()))
                .andExpect(jsonPath("$.email").value(clienteModel.getEmail()));
    }

    @DisplayName("Dado Um ClienteId Inválido Quando BuscarPorId Deve Lançar Exceção Quando Cliente Não Encontrado")
    @Test
    void dadoUmClienteIdQuandoBuscarPorIdDeveLancarExcecaoQuandoClienteNaoEncontrado() throws Exception{
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willThrow(ClienteNaoEncontradoException.class);
        // When / Act
        ResultActions response = mockMvc.perform(get("/clientes/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());

    }

    @DisplayName("Dado Cliente Atualizado Quando Atualizar Retornar CLiente Atualizado")
    @Test
    void dadoClienteAtualizadoQuandoAtualizarRetornarCLienteAtualizado() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willReturn(clienteModel);
        given(clienteService.atualizar(eq(1L), any(ClienteModel.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        // When / Act
        ClienteModel clienteAtualizado = new ClienteModel(null, "Vera", "vera@email.com", "71-1111-1111", "060-606-906-60", LocalDateTime.now(), null, null );

        ResultActions response = mockMvc.perform(put("/clientes/{id}", 1L, clienteAtualizado)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clienteAtualizado)));
        // Then / Assert

        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$").exists()) // Verifica se o JSON existe
                .andExpect(jsonPath("$.nome").exists()) // Verifica se o campo 'nome' existe
                .andExpect(jsonPath("$.nome").value(clienteAtualizado.getNome()))
                .andExpect(jsonPath("$.email").value(clienteAtualizado.getEmail()));

    }

    @DisplayName("Dado ClienteId Incorreto Quando Atualizar Lançar Exceção De Cliente Nao Encotrado")
    @Test
    void dadoClienteIdIncorretoQuandoAtualizarLancarExcecaoDeClienteNaoEncotrado() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willThrow(ClienteNaoEncontradoException.class);
        given(clienteService.atualizar(eq(1L), any(ClienteModel.class)))
                .willThrow(ClienteNaoEncontradoException.class);
        // When / Act
        ClienteModel clienteAtualizado = new ClienteModel(null, "Vera", "vera@email.com", "71-1111-1111", "060-606-906-60", LocalDateTime.now(), null, null );

        ResultActions response = mockMvc.perform(put("/clientes/{id}",1L, clienteAtualizado)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clienteAtualizado)));
        // Then /

        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }


    @DisplayName("Dado ClienteId Deve Remover Cliente Com Sucesso")
    @Test
    void dadoClienteIdDeveRemoverClienteComSucesso() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willReturn(clienteModel);
        willDoNothing().given(clienteService).removerPorId(1L);
        // When / Act
        ResultActions response = mockMvc.perform(delete("/clientes/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isNoContent())
                .andDo(print());

    }

@DisplayName("Dado ClienteId Inválido Quando RemoverCliente Deve Lançar Exceção")
    @Test
    void dadoClienteIdDeveDeletarClienteComSucesso() throws Exception {
        // Given / Arrange
        given(clienteService.buscarPorId(1L)).willThrow(ClienteNaoEncontradoException.class);

        ResultActions response = mockMvc.perform(delete("/clientes/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());

    }
}
