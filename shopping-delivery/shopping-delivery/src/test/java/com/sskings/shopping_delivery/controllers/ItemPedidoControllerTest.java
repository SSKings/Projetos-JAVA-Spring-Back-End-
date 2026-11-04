package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.EstoqueInsuficienteException;
import com.sskings.shopping_delivery.exceptions.ItemNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.ItemPedidoNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.PedidoNaoEncontradoException;
import com.sskings.shopping_delivery.models.ItemModel;
import com.sskings.shopping_delivery.models.ItemPedidoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.services.ItemPedidoService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemPedidoController.class)
@AutoConfigureMockMvc
public class ItemPedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ItemPedidoService itemPedidoService;

    private ItemPedidoModel itemPedidoModel;
    private ItemModel itemModel;
    private PedidoModel pedidoModel;

    @BeforeEach
    void setUp() {
        itemModel = new ItemModel();
        itemModel.setId(1L);
        itemModel.setNome("Produto Teste");
        itemModel.setPreco(new BigDecimal("29.99"));
        itemModel.setEstoque(100);

        pedidoModel = new PedidoModel();
        pedidoModel.setId(1L);

        itemPedidoModel = new ItemPedidoModel();
        itemPedidoModel.setId(1L);
        itemPedidoModel.setItem(itemModel);
        itemPedidoModel.setPedido(pedidoModel);
        itemPedidoModel.setQuantidade(2L);
        itemPedidoModel.setPrecoUnitario(new BigDecimal("29.99"));
        itemPedidoModel.setSubtotal(new BigDecimal("59.98"));
    }

    // JSON gerado manualmente porque @JsonBackReference bloqueia a serialização do campo pedido
    private String itemPedidoJson() {
        return """
                {
                  "pedido": {"id": 1},
                  "item": {"id": 1, "nome": "Produto Teste", "urlImage": "url", "descricao": "desc", "estoque": 100, "preco": 29.99},
                  "quantidade": 2,
                  "precoUnitario": 29.99,
                  "subtotal": 59.98
                }
                """;
    }

    @DisplayName("Dado ItemPedido Deve Salvar E Retornar O ItemPedido")
    @Test
    void dadoItemPedidoDeveSalvarERetornarOItemPedido() throws Exception {
        // Given / Arrange
        given(itemPedidoService.salvar(any(ItemPedidoModel.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        // When / Act
        ResultActions response = mockMvc.perform(post("/itens-pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemPedidoJson()));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.subtotal").value(59.98));
    }

    @DisplayName("Dado ItemPedido Com Estoque Insuficiente Quando Salvar Deve Lançar Exceção")
    @Test
    void dadoItemPedidoComEstoqueInsuficienteQuandoSalvarDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(itemPedidoService.salvar(any(ItemPedidoModel.class)))
                .willThrow(EstoqueInsuficienteException.class);

        // When / Act
        ResultActions response = mockMvc.perform(post("/itens-pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemPedidoJson()));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Dado ItemPedido Com Item Não Encontrado Quando Salvar Deve Lançar Exceção")
    @Test
    void dadoItemPedidoComItemNaoEncontradoQuandoSalvarDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(itemPedidoService.salvar(any(ItemPedidoModel.class)))
                .willThrow(ItemNaoEncontradoException.class);

        // When / Act
        ResultActions response = mockMvc.perform(post("/itens-pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemPedidoJson()));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isNotFound());
    }

    @DisplayName("Dado ItemPedido Com Pedido Não Encontrado Quando Salvar Deve Lançar Exceção")
    @Test
    void dadoItemPedidoComPedidoNaoEncontradoQuandoSalvarDeveLancarExcecao() throws Exception {
        // Given / Arrange
        given(itemPedidoService.salvar(any(ItemPedidoModel.class)))
                .willThrow(PedidoNaoEncontradoException.class);

        // When / Act
        ResultActions response = mockMvc.perform(post("/itens-pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemPedidoJson()));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isNotFound());
    }

    @DisplayName("Dado Uma Lista De ItensPedido Quando Listar Então Retorne Uma Lista de ItensPedido")
    @Test
    void dadoUmaListaDeItensPedidoQuandoListarEntaoRetorneUmaListaDeItensPedido() throws Exception {
        // Given / Arrange
        ItemPedidoModel itemPedido2 = new ItemPedidoModel();
        itemPedido2.setId(2L);
        itemPedido2.setItem(itemModel);
        itemPedido2.setPedido(pedidoModel);
        itemPedido2.setQuantidade(3L);
        itemPedido2.setPrecoUnitario(new BigDecimal("29.99"));
        itemPedido2.setSubtotal(new BigDecimal("89.97"));
        
        List<ItemPedidoModel> itensPedido = new ArrayList<>();
        itensPedido.addAll(List.of(itemPedidoModel, itemPedido2));
        given(itemPedidoService.listar()).willReturn(itensPedido);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/itens-pedido"));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.size()").value(itensPedido.size()));
    }

    @DisplayName("Dado Um ItemPedidoId Quando BuscarPorId Deve Retornar Um ItemPedido")
    @Test
    void dadoUmItemPedidoIdQuandoBuscarPorIdDeveRetornarUmItemPedido() throws Exception {
        // Given / Arrange
        given(itemPedidoService.buscarItemPedidoPorId(1L)).willReturn(itemPedidoModel);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/itens-pedido/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantidade").value(2));
    }

    @DisplayName("Dado Um ItemPedidoId Inválido Quando BuscarPorId Deve Lançar Exceção")
    @Test
    void dadoUmItemPedidoIdQuandoBuscarPorIdDeveLancarExcecaoQuandoItemPedidoNaoEncontrado() throws Exception {
        // Given / Arrange
        given(itemPedidoService.buscarItemPedidoPorId(1L))
                .willThrow(ItemPedidoNaoEncontradoException.class);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/itens-pedido/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado ItemPedidoId Deve Remover ItemPedido Com Sucesso")
    @Test
    void dadoItemPedidoIdDeveRemoverItemPedidoComSucesso() throws Exception {
        // Given / Arrange
        willDoNothing().given(itemPedidoService).removerPorId(1L);
        
        // When / Act
        ResultActions response = mockMvc.perform(delete("/itens-pedido/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @DisplayName("Dado ItemPedidoId Inválido Quando RemoverItemPedido Deve Lançar Exceção")
    @Test
    void dadoItemPedidoIdInvalidoQuandoRemoverItemPedidoDeveLancarExcecao() throws Exception {
        // Given / Arrange
        willThrow(ItemPedidoNaoEncontradoException.class)
                .given(itemPedidoService).removerPorId(1L);

        // When / Act
        ResultActions response = mockMvc.perform(delete("/itens-pedido/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }
}
