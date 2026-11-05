package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAllByActiveTrue(Pageable pageable);
    Page<Product> findAllByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<Product> findByIdAndActiveTrue(Long id);

    // Admin
    Page<Product> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
    long countByActiveTrue();
    long countByActiveTrueAndStockQuantityLessThanEqual(int quantity);
}
