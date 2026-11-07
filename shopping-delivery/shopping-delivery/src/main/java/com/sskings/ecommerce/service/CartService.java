package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.AddCartItemRequest;
import com.sskings.ecommerce.dto.request.UpdateCartItemRequest;
import com.sskings.ecommerce.dto.response.CartItemResponse;
import com.sskings.ecommerce.dto.response.CartSummaryResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.CartItem;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.CartItemRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CartSummaryResponse getCart(Long userId) {
        return toSummary(cartItemRepository.findAllByUserId(userId));
    }

    @Transactional
    public CartSummaryResponse addItem(AddCartItemRequest request, Long userId) {
        // Trava o usuário antes de "procura o item, senão insere": sem isso, duas adições simultâneas do mesmo produto
        // (duplo clique, duas abas) não encontravam o item e as duas inseriam → violação de unique(user_id, product_id)
        // → 500. Achado no teste de carga (load-tests/prod-like); coberto por ConcurrencyIntegrationTest.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStockQuantity() < request.quantity()) {
            throw new BusinessException("Insufficient stock for: " + product.getName());
        }
        cartItemRepository.findByUserIdAndProductId(userId, request.productId())
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + request.quantity();
                            if (product.getStockQuantity() < newQty) {
                                throw new BusinessException("Insufficient stock for: " + product.getName());
                            }
                            existing.setQuantity(newQty);
                            cartItemRepository.save(existing);
                        },
                        () -> cartItemRepository.save(CartItem.builder()
                                .user(user).product(product).quantity(request.quantity()).build())
                );
        return toSummary(cartItemRepository.findAllByUserId(userId));
    }

    @Transactional
    public CartSummaryResponse updateItem(Long itemId, UpdateCartItemRequest request, Long userId) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        if (item.getProduct().getStockQuantity() < request.quantity()) {
            throw new BusinessException("Insufficient stock");
        }
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return toSummary(cartItemRepository.findAllByUserId(userId));
    }

    @Transactional
    public CartSummaryResponse removeItem(Long itemId, Long userId) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        cartItemRepository.delete(item);
        return toSummary(cartItemRepository.findAllByUserId(userId));
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    private CartSummaryResponse toSummary(List<CartItem> items) {
        List<CartItemResponse> responses = items.stream().map(CartItemResponse::from).toList();
        BigDecimal total = responses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartSummaryResponse(responses, total, responses.size());
    }
}
