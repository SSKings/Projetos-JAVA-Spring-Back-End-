package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Order;
import com.sskings.ecommerce.model.OrderItem;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> findAll(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findAllByStatus(status, pageable)
                : orderRepository.findAllBy(pageable);
        return page.map(AdminOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse findById(Long id) {
        return AdminOrderResponse.from(getOrder(id));
    }

    @Transactional
    public AdminOrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = getOrder(id);
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new BusinessException("Cannot change order status from " + current + " to " + newStatus);
        }
        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        order.setStatus(newStatus);
        return AdminOrderResponse.from(orderRepository.save(order));
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
