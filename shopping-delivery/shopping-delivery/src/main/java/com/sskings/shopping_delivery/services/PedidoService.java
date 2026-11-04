package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.exceptions.ClienteNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.EnderecoNaoEncontradoException;
import com.sskings.shopping_delivery.exceptions.PedidoNaoEncontradoException;
import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.ItemPedidoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.models.StatusPedido;
import com.sskings.shopping_delivery.repositories.ClienteRepository;
import com.sskings.shopping_delivery.repositories.EnderecoRepository;
import com.sskings.shopping_delivery.repositories.PedidoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado."));
        EnderecoModel endereco = enderecoRepository.findById(pedidoModel.getEndereco().getId())
                .orElseThrow(() -> new EnderecoNaoEncontradoException("Endereço não encontrado."));

        pedidoModel.setCliente(cliente);
        pedidoModel.setEndereco(endereco);
        
        // Inicializar status e total se não estiverem definidos
        if (pedidoModel.getStatus() == null) {
            pedidoModel.setStatus(StatusPedido.PENDENTE);
        }
        if (pedidoModel.getTotal() == null) {
            pedidoModel.setTotal(BigDecimal.ZERO);
        }
        
        PedidoModel pedidoSalvo = pedidoRepository.save(pedidoModel);
        
        // Calcular total baseado nos itens
        calcularTotalPedido(pedidoSalvo.getId());
        
        return pedidoRepository.findById(pedidoSalvo.getId()).orElse(pedidoSalvo);
    }

    public Page<PedidoModel> listar(Pageable pageable) {
        return pedidoRepository.findAll(pageable);
    }

    public List<PedidoModel> listarPorClienteId(Long clienteId) {
        return pedidoRepository.findByCliente_Id(clienteId);
    }

    public PedidoModel buscarPorId(long id) {
        return pedidoRepository.findByIdWithItens(id)
                .orElseGet(() -> pedidoRepository.findById(id)
                        .orElseThrow(() -> new PedidoNaoEncontradoException("Pedido não encontrado.")));
    }

    @Transactional
    public PedidoModel atualizar(long id, PedidoModel pedidoModel) {
        PedidoModel pedidoExistente = buscarPorId(id);
        
        // Atualizar apenas campos permitidos
        if (pedidoModel.getCliente() != null && pedidoModel.getCliente().getId() != null) {
            ClienteModel cliente = clienteRepository.findById(pedidoModel.getCliente().getId())
                    .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado."));
            pedidoExistente.setCliente(cliente);
        }
        
        if (pedidoModel.getEndereco() != null && pedidoModel.getEndereco().getId() != null) {
            EnderecoModel endereco = enderecoRepository.findById(pedidoModel.getEndereco().getId())
                    .orElseThrow(() -> new EnderecoNaoEncontradoException("Endereço não encontrado."));
            pedidoExistente.setEndereco(endereco);
        }
        
        if (pedidoModel.getStatus() != null) {
            pedidoExistente.setStatus(pedidoModel.getStatus());
        }
        
        // Recalcular total se necessário
        calcularTotalPedido(id);
        
        return pedidoRepository.save(pedidoExistente);
    }

    @Transactional
    public PedidoModel atualizarStatus(long id, StatusPedido status) {
        PedidoModel pedido = buscarPorId(id);
        pedido.setStatus(status);
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public void calcularTotalPedido(long id) {
        PedidoModel pedido = pedidoRepository.findByIdWithItens(id)
                .orElseThrow(() -> new PedidoNaoEncontradoException("Pedido não encontrado."));
        BigDecimal total = BigDecimal.ZERO;
        
        if (pedido.getItens() != null && !pedido.getItens().isEmpty()) {
            for (ItemPedidoModel itemPedido : pedido.getItens()) {
                total = total.add(itemPedido.getSubtotal());
            }
        }
        
        pedido.setTotal(total);
        pedidoRepository.save(pedido);
    }

    @Transactional
    public void removerPorId(long id) {
        buscarPorId(id);
        pedidoRepository.deleteById(id);
    }
}
