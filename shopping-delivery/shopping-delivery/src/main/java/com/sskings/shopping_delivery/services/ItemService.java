package com.sskings.shopping_delivery.services;

import com.sskings.shopping_delivery.exceptions.ItemNaoEncontradoException;
import com.sskings.shopping_delivery.models.ItemModel;
import com.sskings.shopping_delivery.repositories.ItemRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public ItemModel salvar(ItemModel itemModel) {
        return itemRepository.save(itemModel);
    }

    public Page<ItemModel> listar(Pageable pageable) {
        return itemRepository.findAll(pageable);
    }

    public ItemModel buscarPorId(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNaoEncontradoException("Item não encontrado."));
    }

    @Transactional
    public ItemModel atualizar(Long id, ItemModel itemModel) {
        buscarPorId(id);
        itemModel.setId(id);
        return itemRepository.save(itemModel);
    }

    @Transactional
    public void removerPorId(Long id) {
        buscarPorId(id);
        itemRepository.deleteById(id);
    }
}
