package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request, Long userId) {
        List<CartItem> cartItems = cartItemRepository.findAllByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException("Cart is empty");
        }
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        User user = userRepository.getReferenceById(userId);
        Order order = Order.builder()
                .user(user)
                .address(address)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            Product product = productRepository.findById(ci.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (product.getStockQuantity() < ci.getQuantity()) {
                throw new BusinessException("Insufficient stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - ci.getQuantity());
            productRepository.save(product);

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .quantity(ci.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build());
            total = total.add(subtotal);
        }

        order.setTotal(total);
        Order saved = orderRepository.save(order);
        cartItemRepository.deleteAllByUserId(userId);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAllByUser(Long userId) {
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id, Long userId) {
        return orderRepository.findByIdAndUserIdWithItems(id, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }
}
