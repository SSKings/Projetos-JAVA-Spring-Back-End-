package com.sskings.webapi.carga;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.sskings.webapi.models.Cliente;
import com.sskings.webapi.models.Item;
import com.sskings.webapi.models.Pedido;
import com.sskings.webapi.repositories.ClienteRepository;

@Component
public class RepositoryTest implements ApplicationRunner {
	
	@Autowired
	private ClienteRepository clienteRepository;
	
	private static final long ID_CLIENTE_SERGIO = 11l;
	private static final long ID_CLIENTE_VERA = 22l;
	
	private static final long ID_ITEM1 = 100l;
	private static final long ID_ITEM2 = 101l;
	private static final long ID_ITEM3 = 102l;
	
	private static final long ID_PEDIDO1 = 1000l;
	private static final long ID_PEDIDO2 = 1001l;
	private static final long ID_PEDIDO3 = 1002l;
	
	
    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {

    	System.out.println(">>> Iniciando carga de dados...");
    	Cliente sergio = new Cliente(ID_CLIENTE_SERGIO,"Sérgio Santana","Euclides da Cunha");
    	Cliente vera = new Cliente(ID_CLIENTE_VERA,"Vera Marcia","Curiricí"); 	
    	
    	Item dog1 = new Item(ID_ITEM1,"Quentinha", new BigDecimal(20));
    	Item dog2 = new Item(ID_ITEM2,"Bolo",new BigDecimal(15));
		Item dog3 = new Item(ID_ITEM3,"Pudim",new BigDecimal(10));

    	List<Item> listaPedidoSergio1 = new ArrayList<Item>();
    	listaPedidoSergio1.add(dog1);

    	List<Item> listaPedidoVera1 = new ArrayList<Item>();
    	listaPedidoVera1.add(dog2);
    	listaPedidoVera1.add(dog3);
    	
    	Pedido pedidoDoSergio = new Pedido(ID_PEDIDO1,sergio,listaPedidoSergio1,dog1.getPreco(), LocalDate.now());
    	sergio.novoPedido(pedidoDoSergio);
    	
    	Pedido pedidoDaVera = new Pedido(ID_PEDIDO2,vera,listaPedidoVera1, dog2.getPreco().add(dog3.getPreco()), LocalDate.now());
    	vera.novoPedido(pedidoDaVera);
    	
    	System.out.println(">>> Pedido 1 - Sérgio Santana : "+ pedidoDoSergio);
    	System.out.println(">>> Pedido 2 - Vera Marcia: "+ pedidoDaVera);
    	
       
		clienteRepository.saveAndFlush(vera);
		System.out.println(">>> Gravado cliente 2: "+vera);

		List<Item> listaPedidoFernando2 = new ArrayList<Item>();
		listaPedidoFernando2.add(dog2);
		
    	Pedido pedido2DoSergio  = new Pedido(ID_PEDIDO3,sergio,listaPedidoFernando2,dog2.getPreco(), LocalDate.now());
    	sergio.novoPedido(pedido2DoSergio);
    	
    	clienteRepository.saveAndFlush(sergio);
    	
    	System.out.println(">>> Pedido 2 - Sérgio : "+ pedido2DoSergio);
    	System.out.println(">>> Gravado cliente 1: " + sergio);
		
    }
}
