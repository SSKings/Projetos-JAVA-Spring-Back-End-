package com.sskings.shopping_delivery.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.shopping_delivery.exceptions.ItemNaoEncontradoException;
import com.sskings.shopping_delivery.models.ItemModel;
import com.sskings.shopping_delivery.services.ItemService;
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
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc
public class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ItemService itemService;

    private ItemModel itemModel;

    @BeforeEach
    void setUp() {
        itemModel = new ItemModel();
        itemModel.setId(1L);
        itemModel.setNome("Produto Teste");
        itemModel.setUrlImage("https://example.com/image.jpg");
        itemModel.setDescricao("Descrição do produto teste");
        itemModel.setEstoque(100);
        itemModel.setPreco(new BigDecimal("29.99"));
    }

    @DisplayName("Dado Item Deve Salvar E Retornar O Item")
    @Test
    void dadoItemDeveSalvarERetornarOItem() throws Exception {
        // Given / Arrange
        given(itemService.salvar(any(ItemModel.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));
        
        // When / Act
        ResultActions response = mockMvc.perform(post("/itens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(itemModel)));

        // Then / Assert
        response.andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(itemModel.getNome()))
                .andExpect(jsonPath("$.preco").value(itemModel.getPreco().doubleValue()));
    }

    @DisplayName("Dado Uma Lista De Itens Quando Listar Então Retorne Uma Página de Itens")
    @Test
    void dadoUmaListaDeItensQuandoListarEntaoRetorneUmaListaDeItens() throws Exception {
        // Given / Arrange
        ItemModel item2 = new ItemModel();
        item2.setId(2L);
        item2.setNome("Produto 2");
        item2.setUrlImage("https://example.com/image2.jpg");
        item2.setDescricao("Descrição 2");
        item2.setEstoque(50);
        item2.setPreco(new BigDecimal("19.99"));

        List<ItemModel> itens = new ArrayList<>();
        itens.addAll(List.of(itemModel, item2));
        given(itemService.listar(any(Pageable.class))).willReturn(new PageImpl<>(itens));

        // When / Act
        ResultActions response = mockMvc.perform(get("/itens"));

        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.content.size()").value(itens.size()));
    }

    @DisplayName("Dado Um ItemId Quando BuscarPorId Deve Retornar Um Item")
    @Test
    void dadoUmItemIdQuandoBuscarPorIdDeveRetornarUmItem() throws Exception {
        // Given / Arrange
        given(itemService.buscarPorId(1L)).willReturn(itemModel);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/itens/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.nome").value(itemModel.getNome()))
                .andExpect(jsonPath("$.estoque").value(itemModel.getEstoque()));
    }

    @DisplayName("Dado Um ItemId Inválido Quando BuscarPorId Deve Lançar Exceção Quando Item Não Encontrado")
    @Test
    void dadoUmItemIdQuandoBuscarPorIdDeveLancarExcecaoQuandoItemNaoEncontrado() throws Exception {
        // Given / Arrange
        given(itemService.buscarPorId(1L)).willThrow(ItemNaoEncontradoException.class);
        
        // When / Act
        ResultActions response = mockMvc.perform(get("/itens/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado Item Atualizado Quando Atualizar Retornar Item Atualizado")
    @Test
    void dadoItemAtualizadoQuandoAtualizarRetornarItemAtualizado() throws Exception {
        // Given / Arrange
        ItemModel itemAtualizado = new ItemModel();
        itemAtualizado.setNome("Produto Atualizado");
        itemAtualizado.setUrlImage("https://example.com/image-updated.jpg");
        itemAtualizado.setDescricao("Descrição atualizada");
        itemAtualizado.setPreco(new BigDecimal("39.99"));
        itemAtualizado.setEstoque(150);
        
        ItemModel itemRetornado = new ItemModel();
        itemRetornado.setId(1L);
        itemRetornado.setNome("Produto Atualizado");
        itemRetornado.setUrlImage("https://example.com/image-updated.jpg");
        itemRetornado.setDescricao("Descrição atualizada");
        itemRetornado.setPreco(new BigDecimal("39.99"));
        itemRetornado.setEstoque(150);
        
        given(itemService.atualizar(eq(1L), any(ItemModel.class)))
                .willReturn(itemRetornado);
        
        // When / Act
        ResultActions response = mockMvc.perform(put("/itens/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(itemAtualizado)));
        
        // Then / Assert
        response
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$").exists());
    }

    @DisplayName("Dado ItemId Incorreto Quando Atualizar Lançar Exceção De Item Não Encontrado")
    @Test
    void dadoItemIdIncorretoQuandoAtualizarLancarExcecaoDeItemNaoEncontrado() throws Exception {
        // Given / Arrange
        given(itemService.atualizar(eq(1L), any(ItemModel.class)))
                .willThrow(ItemNaoEncontradoException.class);
        
        // When / Act
        ResultActions response = mockMvc.perform(put("/itens/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(itemModel)));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @DisplayName("Dado ItemId Deve Remover Item Com Sucesso")
    @Test
    void dadoItemIdDeveRemoverItemComSucesso() throws Exception {
        // Given / Arrange
        willDoNothing().given(itemService).removerPorId(1L);
        
        // When / Act
        ResultActions response = mockMvc.perform(delete("/itens/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @DisplayName("Dado ItemId Inválido Quando RemoverItem Deve Lançar Exceção")
    @Test
    void dadoItemIdInvalidoQuandoRemoverItemDeveLancarExcecao() throws Exception {
        // Given / Arrange
        willThrow(ItemNaoEncontradoException.class).given(itemService).removerPorId(1L);

        // When / Act
        ResultActions response = mockMvc.perform(delete("/itens/{id}", 1L));
        
        // Then / Assert
        response
                .andExpect(status().isNotFound())
                .andDo(print());
    }
}
