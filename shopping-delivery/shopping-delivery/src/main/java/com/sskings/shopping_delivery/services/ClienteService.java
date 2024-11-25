package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.exceptions.EmailExistenteException;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional
    public ClienteModel salvar(ClienteModel clienteModel) {
        if (existePorEmail(clienteModel.getEmail())){
            throw new EmailExistenteException("Endereço de e-mail já possui um cadastro");
        }
        return clienteRepository.save(clienteModel);
    }

    public List<ClienteModel> listar() {
        List<ClienteModel> clientes = clienteRepository.findAll();
        if (clientes.isEmpty()){
            return new ArrayList<>();
        }
        return clientes;
    }

    public ClienteModel buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado."));
    }

    public ClienteModel buscarPorIdComEnderecos(Long id){
        return clienteRepository.findByIdWithEnderecos(id)
                .orElseThrow(() -> new RuntimeException("Não encontrado"));
    }

    public ClienteModel buscarPorIdComPedidos(Long id){
        return clienteRepository.findByIdWithPedidos(id)
                .orElseThrow(() -> new RuntimeException("Não encontrado"));
    }

    @Transactional
    public ClienteModel atualizar(Long id, ClienteModel clienteModel) {
        ClienteModel clienteRetornado = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado."));
        clienteModel.setId(clienteRetornado.getId());
        return clienteRepository.save(clienteModel);
    }

    @Transactional
    public void removerPorId(Long id) {
        clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
    }

    public boolean existePorEmail(String email) {
        return clienteRepository.existsByEmail(email);
    }
}
