package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Devolve ao estoque os itens de um pedido cancelado, com UPDATE atômico (sem lost update). */
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;

    /**
     * Devolve em ordem de id do produto, a mesma do checkout (OrderService): transações que travam as mesmas linhas
     * sempre na mesma ordem não formam ciclo de espera (deadlock).
     */
    public void restore(Order order) {
        order.getItems().stream()
                .sorted(java.util.Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> productRepository.incrementStock(item.getProduct().getId(), item.getQuantity()));
    }
}
