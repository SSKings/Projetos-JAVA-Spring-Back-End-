package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Baixa atômica: o banco só decrementa se houver estoque, e a trava de linha do UPDATE serializa
     * checkouts concorrentes. Retorna 0 quando falta estoque (ver security-review.md, C2).
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
    int decrementStock(@org.springframework.data.repository.query.Param("id") Long id,
                       @org.springframework.data.repository.query.Param("qty") int qty);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
    int incrementStock(@org.springframework.data.repository.query.Param("id") Long id,
                       @org.springframework.data.repository.query.Param("qty") int qty);

    Page<Product> findAllByActiveTrue(Pageable pageable);
    Page<Product> findAllByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<Product> findByIdAndActiveTrue(Long id);

    // Admin
    Page<Product> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
    long countByActiveTrue();
    long countByActiveTrueAndStockQuantityLessThanEqual(int quantity);
}
