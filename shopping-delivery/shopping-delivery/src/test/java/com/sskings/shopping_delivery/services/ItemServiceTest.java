package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.ItemModel;
import com.sskings.shopping_delivery.repositories.ItemRepository;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.when;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    private ItemModel itemModel;

    @BeforeEach
    void setUp() {
        itemModel = new ItemModel(
                1L,
                "ItemTest",
                "UrlImageTest",
                "DescriçãoTest",
                10,
                BigDecimal.valueOf(10.00),
                List.of());
    }

    @DisplayName("Deve Salvar Um Item com Sucesso")
    @Test
    void deveSalvarUmItemComSucesso() {
        // Given / Arrange
        when(itemRepository.save(itemModel)).thenReturn(itemModel);
        // When / Act
        ItemModel itemRetornado = itemService.salvar(itemModel);
        // Then / Assert
        assertNotNull(itemRetornado);
        assertEquals("ItemTest", itemRetornado.getNome());
    }


    @DisplayName("Deve Listar Items")
    @Test
    void deveListarItems() {
        // Given / Arrange
        ItemModel item2 = new ItemModel(2L,"ItemTest2",
                "UrlImageTest2",
                "DescriçãoTest2",
                20,
                BigDecimal.valueOf(20.00),
                List.of());

        when(itemRepository.findAll()).thenReturn(List.of(itemModel, item2));
        // When / Act
        List<ItemModel> listaRetornada = itemService.listar();
        // Then / Assert
        assertNotNull(listaRetornada);
        assertEquals(2, listaRetornada.size());
        verify(itemRepository, times(1)).findAll();
    }


    @DisplayName("Deve Retornar Uma Lista Vazia Quando Não Há Items")
    @Test
    void deveRetornarUmaListaVaziaQuandoNaoHaItems() {
        // Given / Arrange
        when(itemRepository.findAll()).thenReturn(List.of());
        // When / Act
        List<ItemModel> listaRetornada = itemService.listar();
        // Then / Assert
        assertNotNull(listaRetornada);
        assertEquals(0, listaRetornada.size());
        verify(itemRepository, times(1)).findAll();
    }

    @DisplayName("Deve Buscar Um Item Por ID Com Sucesso")
    @Test
    void deveBuscarUmItemPorIdComSucesso() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemModel));
        // When / Act
        ItemModel itemRetornado = itemService.buscarPorId(1L);
        // Then / Assert
        assertNotNull(itemRetornado);
        assertEquals("ItemTest", itemRetornado.getNome());
    }

    @DisplayName("Deve Lançar Exceção Quando Item Não Encontrado Por ID")
    @Test
    void deveLancarExcecaoQuandoItemNaoEncontradoPorId() {
        // Given / Arrange
        when(itemRepository.findById(anyLong())).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> itemService.buscarPorId(anyLong()));
        // Then / Assert
        assertEquals("Item não encontrado.", exception.getMessage());
        verify(itemRepository, times(1)).findById(anyLong());
        verify(itemRepository, never()).save(any(ItemModel.class));
    }

    @DisplayName("Deve Atualizar Item Com Sucesso")
    @Test
    void deveAtualizarItemComSucesso() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemModel));
        when(itemRepository.save(itemModel)).thenReturn(itemModel);
        // When / Act
        itemModel.setNome("ItemTesteAtualiazdo");
        ItemModel itemRetornado = itemService.atualizar(1L, itemModel);
        // Then / Assert
        assertNotNull(itemRetornado);
        assertEquals("ItemTesteAtualiazdo", itemRetornado.getNome());
        assertSame("ItemTesteAtualiazdo", itemRetornado.getNome());
        verify(itemRepository, times(1)).save(itemModel);
    }

    @DisplayName("Deve Lançar Exceção Quando Item Não Encontrado Ao Atualizar")
    @Test
    void deveLancarExcecaoQuandoItemNaoEncontradoAoAtualizar() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(
                RuntimeException.class, () -> itemService.atualizar(1L, any(ItemModel.class)));
        // Then / Assert
        assertEquals("Item não encontrado.", exception.getMessage());
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, never()).save(any(ItemModel.class));
    }

    @DisplayName("Deve Remover Item Por Id Com Sucesso")
    @Test
    void deveRemoverItemPorIdComSucesso() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemModel));
        willDoNothing().given(itemRepository).deleteById(1L);
        // When / Act
        itemService.removerPorId(1L);
        // Then / Assert
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository,times(1)).deleteById(1L);
    }

    @DisplayName("Deve Lançar Exceção Quando Item Não Encontrado Ao Remover Por Id")
    @Test
    void deveLancarExcecaoQuandoItemNaoEncontradoAoRemoverPorId() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> itemService.removerPorId(1L));
        // Then / Assert
        assertEquals("Item não encontrado.", exception.getMessage());
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, never()).deleteById(1L);
    }
}