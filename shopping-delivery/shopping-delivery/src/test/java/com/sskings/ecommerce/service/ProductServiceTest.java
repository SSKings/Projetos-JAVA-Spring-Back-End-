package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.response.ProductResponse;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @InjectMocks ProductService productService;

    private final Pageable pageable = PageRequest.of(0, 12);

    private Product buildProduct(Long id, String name) {
        return Product.builder().id(id).name(name).price(BigDecimal.TEN)
                .stockQuantity(5).active(true).build();
    }

    @Test
    @DisplayName("findAll without search returns active products page")
    void findAll_noSearch_returnsActivePage() {
        Page<Product> page = new PageImpl<>(List.of(buildProduct(1L, "A"), buildProduct(2L, "B")));
        given(productRepository.findAllByActiveTrue(pageable)).willReturn(page);

        Page<ProductResponse> result = productService.findAll(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findAll with blank search also uses standard query")
    void findAll_blankSearch_usesStandardQuery() {
        Page<Product> page = new PageImpl<>(List.of(buildProduct(1L, "A")));
        given(productRepository.findAllByActiveTrue(pageable)).willReturn(page);

        Page<ProductResponse> result = productService.findAll("  ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findAll with search delegates to name-search query")
    void findAll_withSearch_delegatesToSearch() {
        Page<Product> page = new PageImpl<>(List.of(buildProduct(1L, "Alpha")));
        given(productRepository.findAllByActiveTrueAndNameContainingIgnoreCase("al", pageable)).willReturn(page);

        Page<ProductResponse> result = productService.findAll("al", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById returns product when found")
    void findById_found() {
        given(productRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.of(buildProduct(1L, "A")));

        ProductResponse resp = productService.findById(1L);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.name()).isEqualTo("A");
    }

    @Test
    @DisplayName("findById throws when not found or inactive")
    void findById_notFound_throws() {
        given(productRepository.findByIdAndActiveTrue(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
