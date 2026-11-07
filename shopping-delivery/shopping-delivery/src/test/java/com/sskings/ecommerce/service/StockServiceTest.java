package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.OrderItem;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock ProductRepository productRepository;
    @InjectMocks StockService stockService;

    private static OrderItem item(long productId, int qty) {
        return OrderItem.builder().product(Product.builder().id(productId).build()).quantity(qty).build();
    }

    @Test
    @DisplayName("restore devolve em ordem de id do produto, a mesma do checkout (evita deadlock com checkouts simultâneos)")
    void restore_incrementsInProductIdOrder() {
        Order order = Order.builder().items(new ArrayList<>(List.of(item(30, 1), item(5, 2), item(10, 3)))).build();

        stockService.restore(order);

        InOrder inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).incrementStock(5L, 2);
        inOrder.verify(productRepository).incrementStock(10L, 3);
        inOrder.verify(productRepository).incrementStock(30L, 1);
    }
}
