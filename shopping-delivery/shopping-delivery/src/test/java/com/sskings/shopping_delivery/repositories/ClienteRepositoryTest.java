package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.integrationtests.testcontainers.AbstractIntegrationTest;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.models.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles(value = "integration-test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClienteRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ClienteRepository clienteRepository;

    private ClienteModel cliente0;

    @BeforeEach
    public void setup(){

        cliente0 = new ClienteModel(
                null,
                "Sergio",
                "sergiosantanadosreis@gmail.com",
                "00-00000-0000",
                "000.000.000.00",
                LocalDateTime.now(), null, null);

        EnderecoModel endereco0 = new EnderecoModel(
                null, "Rua A", "11B", "Bairro A", "Casa",
                LocalDateTime.now(), cliente0 );

        PedidoModel pedido0 = new PedidoModel(
                null, cliente0, endereco0, LocalDateTime.now(), StatusPedido.PENDENTE, BigDecimal.valueOf(100.00), null);

        cliente0.setEnderecos(List.of(endereco0));
        cliente0.setPedidos(List.of(pedido0));
        clienteRepository.saveAndFlush(cliente0);



    }

    // test[System Under Test]_[Condition or State Change]_[Expected Result]
    @DisplayName("Dado Nome Do Cliente Quando FindByNome Retornar Objeto ClienteModel")
    @Test
    void testDadoNomeDoCliente_quandoFindByNome_retornarObjetoClienteModel() {
        // Given / Arrange
        // When / Act
        ClienteModel clienteRetornado = clienteRepository.findByNome(cliente0.getNome()).get();
        // Then / Assert
        assertNotNull(clienteRetornado);
        assertEquals(cliente0.getNome(), clienteRetornado.getNome());

    }

    // test[System Under Test]_[Condition or State Change]_[Expected Result]
    @DisplayName("Dado Id Do Cliente Quando FindByIdWithEnderecos Retorne ClienteModel Com Enderecos")
    @Test
    void testDadoIdDoCliente_QuandoFindByIdWithEnderecos_RetorneClienteModelComEnderecos() {
        // Given / Arrange

        // When / Act
        ClienteModel clienteRetornado = clienteRepository.findByIdWithEnderecos(cliente0.getId()).get();
        // Then / Assert
        assertNotNull(clienteRetornado);
        assertNotNull(clienteRetornado.getEnderecos());
        assertEquals(clienteRetornado.getEnderecos().size(), 1);
        assertNotNull(clienteRetornado.getEnderecos().get(0).getId());
        assertEquals("Rua A", clienteRetornado.getEnderecos().get(0).getLogradouro());
    }

    @DisplayName("Dado Id Do Cliente Quando FindByIdWithPedidos Retorne ClienteModel Com Pedidos")
    @Test
    void testDadoIdDoCliente_QuandoFindByIdWithPedidos_RetorneClienteModelComPedidos() {
        // Given / Arrange

        // When / Act
        ClienteModel clienteRetornado = clienteRepository.findByIdWithPedidos(cliente0.getId()).get();
        // Then / Assert
        assertNotNull(clienteRetornado);
        assertNotNull(clienteRetornado.getPedidos());
        assertEquals(clienteRetornado.getPedidos().size(), 1);
        assertNotNull(clienteRetornado.getPedidos().get(0).getId());
        assertEquals(BigDecimal.valueOf(100.00), clienteRetornado.getPedidos().get(0).getTotal());
    }

}
