package com.sskings.webapi.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sskings.webapi.models.Pedido;
import com.sskings.webapi.repositories.PedidoRepository;

import jakarta.transaction.Transactional;

@Service
public class PedidoService {
	
	private final PedidoRepository pedidoRepository;

    PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }
    
    public List<Pedido> findAll(){
    	return pedidoRepository.findAll();
    }
	
    public Optional<Pedido> findById(Long id){
    	return pedidoRepository.findById(id);
    }
    
    @Transactional
    public Pedido save(Pedido pedido) {
    	return pedidoRepository.save(pedido);
    }
    
    @Transactional
    public void delete(Pedido pedido) {
    	pedidoRepository.delete(pedido);
    }
    
    @Transactional
    public void deleteById(Long id) {
    	pedidoRepository.deleteById(id);
    }
    
    

}
