package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role,
        boolean active,
        LocalDateTime createdAt,
        long orderCount
) {
    public static UserResponse from(User u, long orderCount) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.isActive(), u.getCreatedAt(), orderCount);
    }
}
