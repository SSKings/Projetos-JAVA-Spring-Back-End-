package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks AdminDashboardService service;

    @Test
    void getStats_aggregatesRepositories() {
        given(productRepository.count()).willReturn(12L);
        given(productRepository.countByActiveTrue()).willReturn(10L);
        given(productRepository.countByActiveTrueAndStockQuantityLessThanEqual(AdminDashboardService.LOW_STOCK_THRESHOLD)).willReturn(2L);
        given(userRepository.countByRole(Role.CUSTOMER)).willReturn(30L);
        given(orderRepository.count()).willReturn(40L);
        given(orderRepository.countByStatus(OrderStatus.PENDING)).willReturn(3L);
        given(orderRepository.sumRevenue()).willReturn(new BigDecimal("999.90"));

        var stats = service.getStats();

        assertThat(stats.totalProducts()).isEqualTo(12);
        assertThat(stats.activeProducts()).isEqualTo(10);
        assertThat(stats.lowStockProducts()).isEqualTo(2);
        assertThat(stats.totalCustomers()).isEqualTo(30);
        assertThat(stats.totalOrders()).isEqualTo(40);
        assertThat(stats.pendingOrders()).isEqualTo(3);
        assertThat(stats.revenue()).isEqualByComparingTo("999.90");
    }
}
