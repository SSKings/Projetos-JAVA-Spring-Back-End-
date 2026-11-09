package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.request.ProductRequest;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock com.sskings.ecommerce.service.AuditService auditService;
    @InjectMocks AdminProductService service;

    private final Pageable pageable = PageRequest.of(0, 10);

    private Product product(boolean active) {
        return Product.builder().id(1L).name("Caneca").price(BigDecimal.TEN).stockQuantity(4).active(active).build();
    }

    @Test
    @DisplayName("Lista inclui produtos inativos")
    void findAll_noSearch_returnsAll() {
        given(productRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(product(false))));

        var page = service.findAll(null, pageable);

        assertThat(page.getContent()).singleElement().extracting("active").isEqualTo(false);
    }

    @Test
    @DisplayName("Busca por nome")
    void findAll_withSearch() {
        given(productRepository.findAllByNameContainingIgnoreCase("can", pageable))
                .willReturn(new PageImpl<>(List.of(product(true))));

        assertThat(service.findAll("can", pageable).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cria produto ativo por padrão e com nome sem espaços extras")
    void create_defaultsActive() {
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.create(new ProductRequest("  Caneca ", "desc", null, new BigDecimal("29.90"), 3, null));

        assertThat(resp.name()).isEqualTo("Caneca");
        assertThat(resp.active()).isTrue();
        assertThat(resp.stockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Atualiza todos os campos")
    void update_success() {
        Product existing = product(true);
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.update(1L, new ProductRequest("Nova", "d", "https://images.unsplash.com/photo-1?w=400",
                new BigDecimal("5.00"), 0, false));

        assertThat(resp.name()).isEqualTo("Nova");
        assertThat(existing.getImageUrl()).isEqualTo("https://images.unsplash.com/photo-1?w=400");
        assertThat(resp.price()).isEqualByComparingTo("5.00");
        assertThat(resp.active()).isFalse();
    }

    @Test
    @DisplayName("R4-8: imagem só dos hosts permitidos (o dono do host veria o IP de cada cliente)")
    void imageHostAllowlist() {
        Product existing = product(true);
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));
        for (String bad : new String[]{"https://rastreador.example/pixel.gif", "http://images.unsplash.com/x",
                "https://images.unsplash.com.evil.example/x", "https://user@images.unsplash.com/x", "https://[::1/x"}) {
            assertThatThrownBy(() -> service.update(1L, new ProductRequest("N", "d", bad, BigDecimal.ONE, 1, true)))
                    .as(bad).hasMessageContaining("Image host not allowed");
        }
        then(productRepository).should(never()).save(any());
        given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        assertThat(service.update(1L, new ProductRequest("N", "d", "https://IMAGES.unsplash.com/y", BigDecimal.ONE, 1, true))
                .name()).isEqualTo("N");
    }

    @Test
    @DisplayName("Desativar é exclusão lógica")
    void deactivate_setsInactive() {
        Product existing = product(true);
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));

        service.deactivate(1L);

        assertThat(existing.isActive()).isFalse();
        verify(productRepository).save(existing);
    }

    @Test
    @DisplayName("Produto inexistente gera 404")
    void findById_notFound() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
