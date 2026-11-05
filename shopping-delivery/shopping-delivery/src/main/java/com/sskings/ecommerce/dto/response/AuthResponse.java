package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;

public record AuthResponse(String token, String name, String email, Role role) {
    public static AuthResponse of(String token, User user) {
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole());
    }
}
