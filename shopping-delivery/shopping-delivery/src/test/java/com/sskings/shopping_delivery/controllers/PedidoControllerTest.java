package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.PedidoNaoEncontradoException;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.models.StatusPedido;
import com.sskings.shopping_delivery.services.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@AutoConfigureMockMvc
public class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PedidoService pedidoService;

    private PedidoModel pedidoModel;
    private ClienteModel clienteModel;
    private EnderecoModel enderecoModel;

    @BeforeEach
    void setUp() {
        clienteModel = new ClienteModel();
        clienteModel.setId(1L);
        clienteModel.setNome("Cliente Teste");
        clienteModel.setEmail("test@email.com");
        clienteModel.setTelefone("123456789");
        clienteModel.setCpf("123.456.789-00");

        enderecoModel = new EnderecoModel();
        enderecoModel.setId(1L);
        enderecoModel.setLogradouro("Rua Test");
        enderecoModel.setNumero("123");
        enderecoModel.setBairro("Bairro Test");
        enderecoModel.setComplemento("Complemento");
        enderecoModel.setCliente(clienteModel);

        pedidoModel = new PedidoModel();
        pedidoModel.setId(1L);
        pedidoModel.setCliente(clienteModel);
        pedidoModel.setEndereco(enderecoModel);
        pedidoModel.setStatus(StatusPedido.PENDENTE);
        pedidoModel.setTotal(new BigDecimal("100.00"));
        pedidoModel.setDataPedido(LocalDateTime.now());
    }

    @DisplayName("Dado Pedido Deve Salvar E Retornar O Pedido")
    @Test
    void dadoPedidoDeveSalvarERetornarOPedido() throws Exception {
        // Given / Arrange
        given(pedidoService.salvar(any(PedidoModel.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));
        
        // When / Act
        ResultActions response = mockMvc.perform(post("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedidoModel)));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @DisplayName("Dado Uma Lista De Pedidos Quando Listar Então Retorne Uma Página de Pedidos")
    @Test
    void dadoUmaListaDePedidosQuandoListarEntaoRetorneUmaListaDePedidos() throws Exception {
        // Given / Arrange
        PedidoModel pedido2 = new PedidoModel();
        pedido2.setId(2L);
        pedido2.setCliente(clienteModel);
        pedido2.setEndereco(enderecoModel);
        pedido2.setStatus(StatusPedido.APROVADO);
        pedido2.setTotal(new BigDecimal("200.00"));

        List<PedidoModel> pedidos = new ArrayList<>();
        pedidos.addAll(List.of(pedidoModel, pedido2));
        given(pedidoService.listar(any(Pageable.class))).willReturn(new PageImpl<>(pedidos));

        // When / Act
        ResultActions response = mockMvc.perform(get("/pedidos"));

        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.content.size()").value(pedidos.size()));
    }

    @DisplayName("Dado Um PedidoId Quando BuscarPorId Deve Retornar Um Pedido")
    @Test
    void dadoUmPedidoIdQuandoBuscarPorIdDeveRetornarUmPedido() throws Exception {
        // Given / Arrange
        given(pedidoService.buscarPorId(1L)).willReturn(pedidoModel);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/pedidos/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @DisplayName("Dado Um PedidoId Inválido Quando BuscarPorId Deve Lançar Exceção")
    @Test
    void dadoUmPedidoIdQuandoBuscarPorIdDeveLancarExcecaoQuandoPedidoNaoEncontrado() throws Exception {
        // Given / Arrange
        given(pedidoService.buscarPorId(1L)).willThrow(PedidoNaoEncontradoException.class);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/pedidos/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado Pedido Atualizado Quando Atualizar Retornar Pedido Atualizado")
    @Test
    void dadoPedidoAtualizadoQuandoAtualizarRetornarPedidoAtualizado() throws Exception {
        // Given / Arrange
        PedidoModel pedidoAtualizado = new PedidoModel();
        pedidoAtualizado.setCliente(clienteModel);
        pedidoAtualizado.setEndereco(enderecoModel);
        pedidoAtualizado.setStatus(StatusPedido.APROVADO);
        
        PedidoModel pedidoRetornado = new PedidoModel();
        pedidoRetornado.setId(1L);
        pedidoRetornado.setCliente(clienteModel);
        pedidoRetornado.setEndereco(enderecoModel);
        pedidoRetornado.setStatus(StatusPedido.APROVADO);
        pedidoRetornado.setTotal(new BigDecimal("100.00"));
        
        given(pedidoService.atualizar(eq(1L), any(PedidoModel.class)))
                .willReturn(pedidoRetornado);
        
        // When / Act
        ResultActions response = mockMvc.perform(put("/pedidos/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedidoAtualizado)));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$").exists());
    }

    @DisplayName("Dado PedidoId Deve Atualizar Status Com Sucesso")
    @Test
    void dadoPedidoIdDeveAtualizarStatusComSucesso() throws Exception {
        // Given / Arrange
        pedidoModel.setStatus(StatusPedido.APROVADO);
        given(pedidoService.atualizarStatus(1L, StatusPedido.APROVADO)).willReturn(pedidoModel);
        
        // When / Act
        ResultActions response = mockMvc.perform(patch("/pedidos/{id}/status", 1L)
                .param("status", "APROVADO"));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.status").value("APROVADO"));
    }

    @DisplayName("Dado PedidoId Inválido Quando Atualizar Status Deve Lançar Exceção")
    @Test
    void dadoPedidoIdInvalidoQuandoAtualizarStatusDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(pedidoService.atualizarStatus(1L, StatusPedido.APROVADO))
                .willThrow(PedidoNaoEncontradoException.class);
        
        // When / Act
        ResultActions response = mockMvc.perform(patch("/pedidos/{id}/status", 1L)
                .param("status", "APROVADO"));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado PedidoId Deve Remover Pedido Com Sucesso")
    @Test
    void dadoPedidoIdDeveRemoverPedidoComSucesso() throws Exception {
        // Given / Arrange
        willDoNothing().given(pedidoService).removerPorId(1L);
        
        // When / Act
        ResultActions response = mockMvc.perform(delete("/pedidos/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @DisplayName("Dado PedidoId Inválido Quando RemoverPedido Deve Lançar Exceção")
    @Test
    void dadoPedidoIdInvalidoQuandoRemoverPedidoDeveLancarExcecao() throws Exception {
        // Given / Arrange
        willThrow(PedidoNaoEncontradoException.class).given(pedidoService).removerPorId(1L);

        // When / Act
        ResultActions response = mockMvc.perform(delete("/pedidos/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }
}
