package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.dto.response.UserResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(String search, Pageable pageable) {
        Page<User> page = (search != null && !search.isBlank())
                ? userRepository.findAllByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)
                : userRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(getUser(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findOrders(Long userId) {
        getUser(userId);
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long currentAdminId) {
        User user = getUser(id);
        if (Objects.equals(id, currentAdminId)) {
            throw new BusinessException("You cannot change your own account");
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        return toResponse(userRepository.save(user));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, orderRepository.countByUserId(user.getId()));
    }
}
