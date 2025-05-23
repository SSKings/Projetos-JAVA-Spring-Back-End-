package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.exceptions.ClienteNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.CpfExistenteException;
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
        if (existePorCpf(clienteModel.getCpf())){
            throw new CpfExistenteException("Cpf já cadastrado.");
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
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado."));
    }

    public ClienteModel buscarPorIdComEnderecos(Long id){
        return clienteRepository.findByIdWithEnderecos(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado"));
    }

    public ClienteModel buscarPorIdComPedidos(Long id){
        return clienteRepository.findByIdWithPedidos(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado"));
    }

    @Transactional
    public ClienteModel atualizar(Long id, ClienteModel clienteModel) {
        ClienteModel clienteRetornado = buscarPorId(id);
        clienteModel.setId(clienteRetornado.getId());
        return clienteRepository.save(clienteModel);
    }

    @Transactional
    public void removerPorId(Long id) {
        buscarPorId(id);
        clienteRepository.deleteById(id);
    }

    public boolean existePorEmail(String email) {
        return clienteRepository.existsByEmail(email);
    }

    public boolean existePorCpf(String cpf) {
        return clienteRepository.existsByCpf(cpf);
    }
}
