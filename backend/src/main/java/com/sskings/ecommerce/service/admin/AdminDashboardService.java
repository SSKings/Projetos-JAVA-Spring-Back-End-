package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.response.DashboardResponse;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getStats() {
        return new DashboardResponse(
                productRepository.count(),
                productRepository.countByActiveTrue(),
                productRepository.countByActiveTrueAndStockQuantityLessThanEqual(LOW_STOCK_THRESHOLD),
                userRepository.countByRole(Role.CUSTOMER),
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.sumRevenue()
        );
    }
}
