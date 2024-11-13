package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.*;
import com.sskings.shopping_delivery.repositories.ItemPedidoRepository;
import com.sskings.shopping_delivery.repositories.ItemRepository;
import com.sskings.shopping_delivery.repositories.PedidoRepository;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.BDDMockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class ItemPedidoServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ItemPedidoRepository itemPedidoRepository;

    @InjectMocks
    private ItemPedidoService itemPedidoService;

    private ItemPedidoModel itemPedidoModel;
    @BeforeEach
    public void setUp() {
               
        ClienteModel clienteModel = new ClienteModel(1L, "cliente_test", "test@email.com",
                "00-00000-0000", "000.000.000-00", LocalDateTime.now(),
                List.of(), List.of());
        
        EnderecoModel enderecoModel = new EnderecoModel(
                1L,
                "Rua Test",
                "B12",
                "Bairro Test",
                "Complemento",
                LocalDateTime.now(),
                clienteModel
        );
        
        PedidoModel pedidoModel = new PedidoModel(
                1L, clienteModel, 
                enderecoModel,
                LocalDateTime.now(),
                StatusPedido.PENDENTE,
                BigDecimal.valueOf(100),
                List.of()
        );

        ItemModel itemModel = new ItemModel(
                1L, "esp32", "urldaimage",
                "placa de desenvolvimento", 10, BigDecimal.valueOf(40), List.of()

        );
        
        itemPedidoModel = new ItemPedidoModel(
                1L, pedidoModel, itemModel, 1L, BigDecimal.valueOf(40), null
        );
    }
    
    
    @DisplayName("Deve Salvar um ItemPedido com Sucesso.")
    @Test
    void deveSalvarUmItemPedidoComSucesso() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemPedidoModel.getItem()));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(itemPedidoModel.getPedido()));
        when(itemPedidoRepository.save(itemPedidoModel)).thenReturn(itemPedidoModel);
        // When / Act
        ItemPedidoModel itemPedidoRetornado = itemPedidoService.salvar(itemPedidoModel);
        // Then / Assert
        assertNotNull(itemPedidoRetornado);
        assertEquals(1L, itemPedidoRetornado.getId());
        assertEquals("cliente_test", itemPedidoRetornado.getPedido().getCliente().getNome());
        assertTrue(itemPedidoRetornado.getItem().getEstoque() == 9);
    }

    @DisplayName("Deve Lançar Exceção Quando Não Encontrar Item Ao Salvar")
    @Test
    void deveLancarExcecaoQuandoNaoEncontrarItemAoSalvar() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> itemPedidoService.salvar(itemPedidoModel));
        // Then / Assert
        assertNotNull(exception);
        assertEquals("Item não encontrado.", exception.getMessage());
        verify(itemRepository, times(1)).findById(1L);
        verify(itemPedidoRepository, never()).save(itemPedidoModel);
    }

    @DisplayName("Deve Lançar Exceção Quando Não Encontrar Pedido Ao Salvar")
    @Test
    void deveLancarExcecaoQuandoNaoEncontrarPedidoAoSalvar() {
        // Given / Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemPedidoModel.getItem()));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> itemPedidoService.salvar(itemPedidoModel));
        // Then / Assert
        assertNotNull(exception);
        assertEquals("Pedido não encontrado.", exception.getMessage());
        verify(pedidoRepository, times(1)).findById(1L);
        verify(itemPedidoRepository, never()).save(itemPedidoModel);
    }

    @DisplayName("Deve Listar Todos Os Items Pedidos Com Sucesso")
    @Test
    void deveListarTodosOsItemsPedidosComSucesso() {
        // Given / Arrange
        ItemPedidoModel itemPedidoModel2 = new ItemPedidoModel();
        itemPedidoModel2.setId(2L);
        ItemModel item2 = new ItemModel(2L, "teclado","urlimage", "mecanico", 10, BigDecimal.valueOf(100), null );
        itemPedidoModel2.setItem(item2);
        PedidoModel pedido2 = new PedidoModel(2L, itemPedidoModel.getPedido().getCliente(), itemPedidoModel.getPedido().getEndereco(), LocalDateTime.now().plusHours(2), StatusPedido.PENDENTE, BigDecimal.valueOf(100), null);
        itemPedidoModel2.setPedido(pedido2);
        itemPedidoModel2.setQuantidade(2L);
        when(itemPedidoRepository.findAll()).thenReturn(List.of(itemPedidoModel, itemPedidoModel2));
        // When / Act
        List<ItemPedidoModel> itemsPedidos = itemPedidoService.listar();
        // Then / Assert
        assertNotNull(itemsPedidos);
        assertEquals(2, itemsPedidos.size());
        assertEquals("teclado", itemsPedidos.get(1).getItem().getNome());
    }

    @DisplayName("Deve Retornar Uma Lista Vazia Quando Não Há Items Pedidos")
    @Test
    void deveRetornarUmaListaVaziaQuandoNaoHaItemsPedidos() {
        // Given / Arrange
        when(itemPedidoRepository.findAll()).thenReturn(Collections.emptyList());
        // When / Act
        List<ItemPedidoModel> itemsPedidos = itemPedidoService.listar();
        // Then / Assert
        assertNotNull(itemsPedidos);
        assertEquals(0, itemsPedidos.size());
        assertTrue(itemsPedidos.isEmpty());
        verify(itemPedidoRepository, times(1)).findAll();
    }

    @DisplayName("Deve Buscar Um Item Pedido Por ID Com Sucesso")
    @Test
    void deveBuscarUmItemPedidoPorIdComSucesso() {
        // Given / Arrange
        when(itemPedidoRepository.findById(1L)).thenReturn(Optional.of(itemPedidoModel));
        // When / Act
        ItemPedidoModel itemPedidoRetornado = itemPedidoService.buscarItemPedidoPorId(1L);
        // Then / Assert
        assertNotNull(itemPedidoRetornado);
        assertEquals("cliente_test", itemPedidoRetornado.getPedido().getCliente().getNome());
        verify(itemPedidoRepository, times(1)).findById(1L);
    }

    @DisplayName("Deve Lançar Exceção Quando Item Pedido Não Encontrado Por ID")
    @Test
    void deveLancarExcecaoQuandoItemPedidoNaoEncontradoPorId() {
        // Given / Arrange
        when(itemPedidoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> itemPedidoService.buscarItemPedidoPorId(1L));
        // Then / Assert
        assertNotNull(exception);
        assertEquals("registro não encontrado", exception.getMessage());
        verify(itemPedidoRepository, times(1)).findById(1L);
    }

    @DisplayName("Deve Remover Um Item Pedido Por ID Com Sucesso")
    @Test
    void deveRemoverUmItemPedidoPorIdComSucesso() {
        // Given / Arrange
        when(itemPedidoRepository.findById(1L)).thenReturn(Optional.of(itemPedidoModel));
        willDoNothing().given(itemPedidoRepository).deleteById(1L);
        // When / Act
        itemPedidoService.removerPorId(1L);
        // Then / Assert
        verify(itemPedidoRepository, times(1)).findById(1L);
        verify(itemPedidoRepository, times(1)).deleteById(1L);
    }

    @DisplayName("Deve Lançar Uma Exceção Quando Remover Por ID Item Pedido Não Encontrado")
    @Test
    void deveLancarUmaExcecaoQuandoRemoverPorIdItemPedidoNaoEncontrado() {
        // Given / Arrange
        when(itemPedidoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> { itemPedidoService.removerPorId(1L); });
        // Then / Assert
        assertNotNull(exception);
        assertEquals("Item Pedido não encontrado.", exception.getMessage());
        verify(itemPedidoRepository, times(1)).findById(1L);
        verify(itemPedidoRepository, never()).deleteById(1L);
    }
}
