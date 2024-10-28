package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import com.sskings.shopping_delivery.repositories.EnderecoRepository;
import com.sskings.shopping_delivery.repositories.PedidoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final ClienteRepository clienteRepository;

    private final EnderecoRepository enderecoRepository;
    private final PedidoRepository pedidoRepository;

    @Transactional
    public PedidoModel salvar(PedidoModel pedidoModel) {
        ClienteModel cliente = clienteRepository.findById(pedidoModel.getCliente().getId())
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado."));
        EnderecoModel endereco = enderecoRepository.findById(pedidoModel.getEndereco().getId())
                .orElseThrow(() -> new RuntimeException("Endereço não encontrado."));

        pedidoModel.setCliente(cliente);
        pedidoModel.setEndereco(endereco);
        return pedidoRepository.save(pedidoModel);

    }

    public List<PedidoModel> listar() {
        List<PedidoModel> pedidos = pedidoRepository.findAll();
        if (pedidos.isEmpty()) {
            return new ArrayList<>();
        }
        return pedidos;
    }

    public PedidoModel buscarPorId(long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
    }

    public PedidoModel atualizar(long id, PedidoModel pedidoModel) {
        pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
        pedidoModel.setId(id);
        return pedidoRepository.save(pedidoModel);
    }

    public void removerPorId(long id) {
        pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
        pedidoRepository.deleteById(id);
    }
}
