package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.models.StatusPedido;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteService clienteService;

    private ClienteModel clienteModel;

    @BeforeEach
    void setUp() {
        // Configura um cliente com endereços e pedidos
        clienteModel = new ClienteModel();
        clienteModel.setId(null);
        clienteModel.setNome("Cliente Teste");
        clienteModel.setEmail("test@email.com");
        clienteModel.setTelefone("123456789");
        clienteModel.setCpf("123.456.789-00");
        clienteModel.setDataCadastro(LocalDateTime.now());

        // Mock de endereços
        List<EnderecoModel> enderecos = new ArrayList<>();
        EnderecoModel endereco = new EnderecoModel();
        endereco.setId(null);
        endereco.setLogradouro("Rua A");
        endereco.setNumero("12345");
        endereco.setBairro("Bairro Test");
        endereco.setComplemento("Complemento");
        endereco.setCliente(clienteModel);
        endereco.setData_cadastro(LocalDateTime.now());
        enderecos.add(endereco);
        clienteModel.setEnderecos(enderecos);

        // Mock de pedidos
        List<PedidoModel> pedidos = new ArrayList<>();
        PedidoModel pedido = new PedidoModel();
        pedido.setId(null);
        pedido.setCliente(clienteModel);
        pedido.setDataPedido(LocalDateTime.now());
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setEndereco(endereco);
        pedido.setTotal(BigDecimal.valueOf(100.00));
        pedidos.add(pedido);
        clienteModel.setPedidos(pedidos);
    }

    @Test
    void deveSalvarClienteComSucesso() {
        when(clienteRepository.existsByEmail(clienteModel.getEmail())).thenReturn(false);
        when(clienteRepository.save(clienteModel)).thenReturn(clienteModel);

        ClienteModel resultado = clienteService.salvar(clienteModel);

        assertNotNull(resultado);
        assertEquals(clienteModel.getEmail(), resultado.getEmail());
        verify(clienteRepository, times(1)).save(clienteModel);
    }

    @Test
    void deveLancarExcecaoQuandoEmailJaCadastrado() {
        when(clienteRepository.existsByEmail(clienteModel.getEmail())).thenReturn(true);

        Exception exception = assertThrows(RuntimeException.class, () -> clienteService.salvar(clienteModel));

        assertEquals("Endereço de e-mail já possui um cadastro", exception.getMessage());
        verify(clienteRepository, never()).save(any(ClienteModel.class));
    }

    @Test
    void deveListarClientes() {
        List<ClienteModel> clientes = new ArrayList<>();
        clientes.add(clienteModel);

        when(clienteRepository.findAll()).thenReturn(clientes);

        List<ClienteModel> resultado = clienteService.listar();

        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());
        verify(clienteRepository, times(1)).findAll();
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaClientes() {
        when(clienteRepository.findAll()).thenReturn(new ArrayList<>());

        List<ClienteModel> resultado = clienteService.listar();

        assertTrue(resultado.isEmpty());
        verify(clienteRepository, times(1)).findAll();
    }

    @Test
    void deveBuscarClientePorIdComSucesso() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.of(clienteModel));

        ClienteModel resultado = clienteService.buscarPorId(1L);

        assertNotNull(resultado);
        assertEquals(clienteModel.getId(), resultado.getId());
        verify(clienteRepository, times(1)).findById(1L);
    }

    @Test
    void deveLancarExcecaoQuandoClienteNaoEncontradoPorId() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> clienteService.buscarPorId(1L));

        assertEquals("Cliente não encontrado.", exception.getMessage());
        verify(clienteRepository, times(1)).findById(1L);
    }

    @Test
    void deveAtualizarClienteComSucesso() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.of(clienteModel));
        when(clienteRepository.save(clienteModel)).thenReturn(clienteModel);

        ClienteModel resultado = clienteService.atualizar(1L, clienteModel);

        assertNotNull(resultado);
        assertEquals(clienteModel.getId(), resultado.getId());
        verify(clienteRepository, times(1)).save(clienteModel);
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarClienteNaoEncontrado() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> clienteService.atualizar(1L, clienteModel));

        assertEquals("Cliente não encontrado.", exception.getMessage());
        verify(clienteRepository, never()).save(any(ClienteModel.class));
    }

    @Test
    void deveRemoverClientePorIdComSucesso() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.of(clienteModel));

        clienteService.removerPorId(1L);

        verify(clienteRepository, times(1)).deleteById(1L);
    }

    @Test
    void deveLancarExcecaoQuandoRemoverClienteNaoEncontrado() {
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> clienteService.removerPorId(1L));

        assertEquals("Cliente não encontrado.", exception.getMessage());
        verify(clienteRepository, never()).deleteById(anyLong());
    }

    @Test
    void deveBuscarClientePorIdComEnderecos() {
        when(clienteRepository.findByIdWithEnderecos(anyLong())).thenReturn(Optional.of(clienteModel));

        ClienteModel resultado = clienteService.buscarPorIdComEnderecos(1L);

        assertNotNull(resultado);
        assertEquals(clienteModel.getId(), resultado.getId());
        assertNotNull(resultado.getEnderecos());
        assertFalse(resultado.getEnderecos().isEmpty());
        verify(clienteRepository, times(1)).findByIdWithEnderecos(1L);
    }

    @Test
    void deveBuscarClientePorIdComPedidos() {
        when(clienteRepository.findByIdWithPedidos(anyLong())).thenReturn(Optional.of(clienteModel));

        ClienteModel resultado = clienteService.buscarPorIdComPedidos(1L);

        assertNotNull(resultado);
        assertEquals(clienteModel.getId(), resultado.getId());
        assertNotNull(resultado.getPedidos());
        assertFalse(resultado.getPedidos().isEmpty());
        verify(clienteRepository, times(1)).findByIdWithPedidos(1L);
    }
}
