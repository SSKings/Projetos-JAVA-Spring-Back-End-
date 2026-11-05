package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.request.ProductRequest;
import com.sskings.ecommerce.dto.response.AdminProductResponse;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final com.sskings.ecommerce.service.AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AdminProductResponse> findAll(String search, Pageable pageable) {
        Page<Product> page = (search != null && !search.isBlank())
                ? productRepository.findAllByNameContainingIgnoreCase(search, pageable)
                : productRepository.findAll(pageable);
        return page.map(AdminProductResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse findById(Long id) {
        return AdminProductResponse.from(getProduct(id));
    }

    @Transactional
    public AdminProductResponse create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        Product saved = productRepository.save(product);
        auditService.record(null, com.sskings.ecommerce.service.AuditService.PRODUCT_CHANGED, "PRODUCT", saved.getId(), "created");
        return AdminProductResponse.from(saved);
    }

    @Transactional
    public AdminProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        String before = "price " + product.getPrice() + ", stock " + product.getStockQuantity() + ", active " + product.isActive();
        apply(product, request);
        auditService.record(null, com.sskings.ecommerce.service.AuditService.PRODUCT_CHANGED, "PRODUCT", id, "updated (" + before + " -> price "
                + product.getPrice() + ", stock " + product.getStockQuantity() + ", active " + product.isActive() + ")");
        return AdminProductResponse.from(productRepository.save(product));
    }

    /** Exclusão lógica: o produto sai da vitrine mas continua referenciado por pedidos antigos. */
    @Transactional
    public void deactivate(Long id) {
        Product product = getProduct(id);
        product.setActive(false);
        productRepository.save(product);
        auditService.record(null, com.sskings.ecommerce.service.AuditService.PRODUCT_CHANGED, "PRODUCT", id, "deactivated");
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setActive(request.active() == null || request.active());
    }
}
