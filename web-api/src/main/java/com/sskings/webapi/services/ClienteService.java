package com.sskings.webapi.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sskings.webapi.models.Cliente;
import com.sskings.webapi.repositories.ClienteRepository;

import jakarta.transaction.Transactional;

@Service
public class ClienteService {
	
	private final ClienteRepository clienteRepository;

    ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }
    
    public List<Cliente> findAll(){
    	return clienteRepository.findAll();
    }
	
    public Optional<Cliente> findById(Long id){
    	return clienteRepository.findById(id);
    }
    
    public Cliente findByNome(String nome) {
    	return clienteRepository.findByNome(nome);
    }
    
    @Transactional
    public Cliente save(Cliente cliente) {
    	return clienteRepository.save(cliente);
    }
    
    @Transactional
    public void delete(Cliente cliente) {
    	clienteRepository.delete(cliente);
    }
    
    @Transactional
    public void deleteById(Long id) {
    	clienteRepository.deleteById(id);
    }
}
