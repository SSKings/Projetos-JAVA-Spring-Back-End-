package com.sskings.webapi.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sskings.webapi.models.Item;
import com.sskings.webapi.repositories.ItemRepository;

import jakarta.transaction.Transactional;

@Service
public class ItemService {

	private final ItemRepository itemRepository;

    ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }
	
    public List<Item> findAll(){
    	return itemRepository.findAll();
    }
    
    public Optional<Item> findById(Long id){
    	return itemRepository.findById(id);
    }
    
    @Transactional
    public Item save(Item item) {
    	return itemRepository.save(item);
    }
    
    @Transactional
    public void deleteById(Long id) {
    	itemRepository.deleteById(id);
    }
    
	
    
}
