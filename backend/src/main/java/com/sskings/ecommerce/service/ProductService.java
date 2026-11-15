package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.response.ProductResponse;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<ProductResponse> findAll(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return productRepository
                    .findAllByActiveTrueAndNameContainingIgnoreCase(search, pageable)
                    .map(ProductResponse::from);
        }
        return productRepository.findAllByActiveTrue(pageable).map(ProductResponse::from);
    }

    public ProductResponse findById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }
}
