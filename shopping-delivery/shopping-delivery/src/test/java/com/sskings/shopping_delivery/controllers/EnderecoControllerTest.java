package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.ClienteNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.EnderecoNaoEncontradoException;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import com.sskings.shopping_delivery.services.EnderecoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnderecoController.class)
@AutoConfigureMockMvc
public class EnderecoControllerTest {

    @MockBean
    private EnderecoService enderecoService;

    @MockBean
    private ClienteRepository clienteRepository;

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
        given(clienteRepository.findById(1L)).willReturn(Optional.of(enderecoModel.getCliente()));
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

    @DisplayName("Dado Endereço Com UsuarioId Inválido Quando Salvar Lançar Exceção")
    @Test
    void dadoEnderecoComUsuarioIdInvalidoQuandoSalvarLancarExcecao() throws Exception {
        // Given / Arrange
        given(clienteRepository.findById(anyLong())).willThrow(ClienteNaoEncontradoException.class);
        given(enderecoService.salvar(any(EnderecoModel.class))).willThrow(ClienteNaoEncontradoException.class);
        // When / Act
        ResultActions response = mockMvc.perform(post("/enderecos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enderecoModel)));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado Uma Lista De Endereços Quando Listar Então Retorne Uma Lista De Endereços")
    @Test
    void dadoUmaListaDeEnderecosQuandoListarEntaoRetorneUmaListaDeEnderecos() throws Exception {
        // Given / Arrange
        EnderecoModel enderecoModel2 = new EnderecoModel(
                1L,
                "Rua Test",
                "B12",
                "Bairro Test",
                "Complemento",
                LocalDateTime.now(),
                enderecoModel.getCliente()

        );
        List<EnderecoModel> enderecos = new ArrayList<>();
        enderecos.addAll(List.of(enderecoModel, enderecoModel2));
        given(enderecoService.listar()).willReturn(enderecos);

        // When / Act
        ResultActions response = mockMvc.perform(get("/enderecos"));

        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.size()").value(enderecos.size()));
    }

    @DisplayName("Dado Uma Lista Vazia Quando Listar Então Retorne Uma Lista Vazia")
    @Test
    void dadoUmaListaVaziaQuandoListarEntaoRetorneUmaListaVazia() throws Exception {
        // Given / Arrange
        given(enderecoService.listar()).willReturn(Collections.emptyList());

        // When / Act
        ResultActions response = mockMvc.perform(get("/enderecos")
                .contentType(MediaType.APPLICATION_JSON));

        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(content().json("[]"));
    }

    @DisplayName("Dado Um EnderecoId Quando BuscarPorId Deve Retornar Um Endereço")
    @Test
    void dadoUmEnderecoIdQuandoBuscarPorIdDeveRetornarUmEndereco() throws Exception {
        // Given / Arrange
        given(enderecoService.buscarPorId(1L)).willReturn(enderecoModel);
        // When / Act
        ResultActions response = mockMvc.perform(get("/enderecos/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.logradouro").value(enderecoModel.getLogradouro()));
    }

    @DisplayName("Dado Um EnderecoId Inválido Quando BuscarPorId Deve Lançar Exceção.")
    @Test
    void dadoUmEnderecoIdInvalidoQuandoBuscarPorIdDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(enderecoService.buscarPorId(1L)).willThrow(EnderecoNaoEncontradoException.class);
        // When / Act
        ResultActions response = mockMvc.perform(get("/enderecos/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado Endereço Atualizado Quando Atualizar Deve Retornar Endereço Atualizado")
    @Test
    void dadoEnderecoAtualizadoQuandoAtualizarDeveRetornarEnderecoAtualizado() throws Exception {
        // Given / Arrange
        enderecoModel.setLogradouro("Rua Atualizada");
        given(enderecoService.atualizar(1L, enderecoModel))
                .willAnswer(invocation -> invocation.getArgument(1));
        // When / Act
        ResultActions response = mockMvc.perform(put("/enderecos/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enderecoModel)));
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.logradouro").value("Rua Atualizada"));
    }

    @DisplayName("Dado EnderecoId inválido Quando Atualizar Deve Lançar Exceção")
    @Test
    void dadoEnderecoIdInvalidoQuandoAtualizarDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(clienteRepository.findById(1L)).willReturn(Optional.of(enderecoModel.getCliente()));
        enderecoModel.setLogradouro("Rua Atualizada");
        given(enderecoService.atualizar(1L, enderecoModel)).willThrow(EnderecoNaoEncontradoException.class);
        // When / Act
        ResultActions response = mockMvc.perform(put("/enderecos/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON).
                content(objectMapper.writeValueAsString(enderecoModel)));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado ClienteId inválido Quando Atualizar Deve Lançar Exceção")
    @Test
    void dadoClienteIdInvalidoQuandoAtualizarDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(clienteRepository.findById(1L)).willThrow(ClienteNaoEncontradoException.class);
        enderecoModel.setLogradouro("Rua Atualizada");
        given(enderecoService.atualizar(1L, enderecoModel)).willThrow(ClienteNaoEncontradoException.class);
        // When / Act
        ResultActions response = mockMvc.perform(put("/enderecos/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enderecoModel)));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado EndereçoId Deve Remover Endereço Com Sucesso.")
    @Test
    void dadoEnderecoIdDeveRemoverEnderecoComSucesso() throws Exception {
        // Given / Arrange
        given(enderecoService.buscarPorId(1L)).willReturn(enderecoModel);
        willDoNothing().given(enderecoService).removerPorId(1L);
        // When / Act
        ResultActions response = mockMvc.perform(delete("/enderecos/{id}", 1L));
        // Then / Assert
        response
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @DisplayName("Dado EndereçoId Inválido Quando RemoverPorId Deve Lançar Exceção")
    @Test
    void dadoEnderecoIdInvalidoQuandoRemoverPorIdDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(enderecoService.buscarPorId(anyLong())).willThrow(ClienteNaoEncontradoException.class);

        // When / Act
        ResultActions response = mockMvc.perform(delete("/enderecos/{id}", anyLong()));
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }
}
