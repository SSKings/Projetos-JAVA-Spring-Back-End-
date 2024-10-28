package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.*;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import com.sskings.shopping_delivery.repositories.EnderecoRepository;
import com.sskings.shopping_delivery.repositories.PedidoRepository;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static  org.mockito.BDDMockito.*;

import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
public class PedidoServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EnderecoRepository enderecoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private PedidoModel pedidoModel;

    @BeforeEach
    void setUp(){

        ClienteModel clienteModel = new ClienteModel(
                1L,
                "Cliente Teste",
                "test@email.com",
                "123456789",
                "123.456.789-00",
                LocalDateTime.now(),
                new ArrayList<>(), new ArrayList<>()
        );


        EnderecoModel enderecoModel = new EnderecoModel(
                1L,
                "Rua Test",
                "B12",
                "Bairro Test",
                "Complemento",
                LocalDateTime.now(),
                clienteModel
        );

        ItemModel itemModel = new ItemModel(
                1L,
                "ItemTest",
                "UrlImageTest",
                "DescriçãoTest",
                10,
                BigDecimal.valueOf(10.00),
                new ArrayList<>()


        );

        ItemPedidoModel itemPedidoModel = new ItemPedidoModel(
                1L, pedidoModel, itemModel, 10L, BigDecimal.valueOf(10), BigDecimal.ZERO
        );

        pedidoModel = new PedidoModel(
                1L,
                clienteModel,
                enderecoModel,
                LocalDateTime.now(),
                StatusPedido.PENDENTE,
                BigDecimal.valueOf(100),
                List.of(itemPedidoModel)
        );
    }

    @DisplayName("Deve Salvar Um Pedido Com Sucesso.")
    @Test
    void deveSalvarUmPedidoComSucesso() {
        // Given / Arrange
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(pedidoModel.getCliente()));
        when(enderecoRepository.findById(1L)).thenReturn(Optional.of(pedidoModel.getEndereco()));
        when(pedidoRepository.save(pedidoModel)).thenReturn(pedidoModel);
        // When / Act
        PedidoModel pedidoRetornado = pedidoService.salvar(pedidoModel);
        // Then / Assert
        assertNotNull(pedidoRetornado);
        assertEquals(pedidoModel, pedidoRetornado);
        verify(pedidoRepository, times(1)).save(pedidoModel);

    }

    @DisplayName("Deve Listar Pedidos")
    @Test
    void deveListarPedidos() {
        // Given / Arrange
        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoModel, new PedidoModel(), new PedidoModel()));
        // When / Act
        List<PedidoModel> pedidos = pedidoService.listar();
        // Then / Assert
        assertNotNull(pedidos);
        assertEquals(pedidoModel, pedidos.get(0));
        assertEquals(3, pedidos.size());
        verify(pedidoRepository, times(1)).findAll();
    }

    @DisplayName("Deve Retornar Uma Lista Vazia Quando Não Há Pedidos")
    @Test
    void deveRetornarUmaListaVaziaQuandoNaoHaPedidos() {
        // Given / Arrange
        when(pedidoRepository.findAll()).thenReturn(Collections.emptyList());
        // When / Act
        List<PedidoModel> listaRetornada = pedidoService.listar();
        // Then / Assert
        assertNotNull(listaRetornada);
        assertEquals(0, listaRetornada.size());
        assertTrue(listaRetornada.isEmpty());
        verify(pedidoRepository, times(1)).findAll();
    }

    @DisplayName("Deve Buscar Um Pedido Por Id Com Sucesso")
    @Test
    void deveBuscarUmPedidoPorIdComSucesso() {
        // Given / Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoModel));
        // When / Act
        PedidoModel pedidoRetornando = pedidoService.buscarPorId(1L);
        // Then / Assert
        assertNotNull(pedidoRetornando);
        assertEquals(pedidoModel, pedidoRetornando);
        assertEquals(StatusPedido.PENDENTE, pedidoRetornando.getStatus());
    }

    @DisplayName("Deve Lancar Excecao De Pedido Nao Encontrado Por Id")
    @Test
    void deveLancarExcecaoQuandoPedidoNaoEncontradoPorId() {
        // Given / Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> pedidoService.buscarPorId(1L));
        // Then / Assert
        assertNotNull(exception);
        assertEquals("Pedido não encontrado.", exception.getMessage());
        verify(pedidoRepository, times(1)).findById(1L);
    }

    @DisplayName("Deve Alterar O Pedido Com Sucesso.")
    @Test
    void deveAtualizarOPedidoComSuceso() {
        // Given / Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoModel));
        when(pedidoRepository.save(pedidoModel)).thenReturn(pedidoModel);
        // When / Act
        pedidoModel.setStatus(StatusPedido.CANCELADO);
        PedidoModel pedidoRetornado = pedidoService.atualizar(1L, pedidoModel);
        // Then / Assert
        assertNotNull(pedidoRetornado);
        assertEquals(pedidoModel, pedidoRetornado);
        assertEquals(StatusPedido.CANCELADO, pedidoRetornado.getStatus());
        verify(pedidoRepository, times(1)).findById(1L);
        verify(pedidoRepository, times(1)).save(pedidoModel);
    }

    @DisplayName("Deve Lançar Exceção De Pedido Não Encontrado Quando Atualizar")
    @Test
    void deveLancarExcecaoDePedidoNaoEncontradoQuandoAtualizar() {
        // Given / Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> pedidoService.atualizar(1L, pedidoModel));
        // Then / Assert
        assertNotNull(exception);
        assertEquals("Pedido não encontrado.", exception.getMessage());
        verify(pedidoRepository, times(1)).findById(1L);
        verify(pedidoRepository, never()).save(pedidoModel);
    }

    // test[System Under Test]_[Condition or State Change]_[Expected Result]
    @DisplayName("Deve Remover Um Pedido Por Id Com Sucesso")
    @Test
    void deveRemoverUmPedidoPorIdComSucesso() {
        // Given / Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoModel));
        willDoNothing().given(pedidoRepository).deleteById(1L);
        // When / Act
        pedidoService.removerPorId(1L);
        // Then / Assert
        assertNotNull(pedidoRepository.findById(1L));
        verify(pedidoRepository, times(1)).deleteById(1L);
    }
}
