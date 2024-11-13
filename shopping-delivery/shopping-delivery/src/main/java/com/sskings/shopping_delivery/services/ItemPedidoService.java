package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.models.ItemModel;
import com.sskings.shopping_delivery.models.ItemPedidoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.repositories.ItemPedidoRepository;
import com.sskings.shopping_delivery.repositories.ItemRepository;
import com.sskings.shopping_delivery.repositories.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemPedidoService {

    private final ItemRepository itemRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;

    public ItemPedidoModel salvar(ItemPedidoModel itemPedidoModel) {
        ItemModel item = itemRepository.findById(itemPedidoModel.getItem().getId())
                .orElseThrow(() -> new RuntimeException("Item não encontrado."));
        PedidoModel pedido = pedidoRepository.findById(itemPedidoModel.getPedido().getId())
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado."));
        int novoEstoque = item.getEstoque();
        novoEstoque -= itemPedidoModel.getQuantidade();
        item.setEstoque(novoEstoque);
        itemRepository.save(item);
        return itemPedidoRepository.save(itemPedidoModel);
    }


    public List<ItemPedidoModel> listar() {
        List<ItemPedidoModel> itemsPedidos = itemPedidoRepository.findAll();
        if (itemsPedidos.isEmpty()){
            return new ArrayList<>();
        }
        return itemsPedidos;
    }

    public ItemPedidoModel buscarItemPedidoPorId(long id) {
        return itemPedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("registro não encontrado"));
    }

    public void removerPorId(long id) {
        itemPedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item Pedido não encontrado."));
        itemPedidoRepository.deleteById(id);
    }
}


