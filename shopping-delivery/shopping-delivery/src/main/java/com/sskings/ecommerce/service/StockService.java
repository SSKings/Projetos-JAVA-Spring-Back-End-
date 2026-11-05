package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.OrderItem;
import com.sskings.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Devolve ao estoque os itens de um pedido cancelado, com UPDATE atômico (sem lost update). */
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;

    public void restore(Order order) {
        for (OrderItem item : order.getItems()) {
            productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
        }
    }
}
