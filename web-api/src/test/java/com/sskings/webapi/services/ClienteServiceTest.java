package com.sskings.webapi.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sskings.webapi.models.Cliente;

@SpringBootTest
public class ClienteServiceTest {
	
	private final ClienteService clienteService;
	
	@Autowired
	public ClienteServiceTest(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@Test
	void findAllTest(){
		List<Cliente> clientes = this.clienteService.findAll();
		assertThat(clientes.size()).isEqualTo(2);
		assertThat(clientes.isEmpty()).isFalse();
	}
	
	@Test
	void findByIdTest() {
		Optional<Cliente> clienteOpt = clienteService.findById(1L);
		assertThat(clienteOpt).isPresent();
		assertThat(clienteOpt.get()).isNotNull();
		assertThat(clienteOpt.get()).isInstanceOf(Cliente.class);
	}
	
	/*@Test
	void saveTest() {
		Cliente cliente = new Cliente();
		cliente.setNome("Netinho");
		cliente.setEndereco("Nova América, Rua 2");
		cliente = this.clienteService.save(cliente);
		assertThat(cliente.getId()).isNotNull();
	}*/
	
	@Test
	void deleteTest() {
		Cliente cliente = clienteService.findByNome("Vera Marcia");
		clienteService.delete(cliente);
		cliente = clienteService.findByNome("Vera Marcia");
		assertThat(cliente).isNull();
	}
}
