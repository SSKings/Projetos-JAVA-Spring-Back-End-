package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;

import java.time.LocalDateTime;

public record ProfileResponse(Long id, String name, String email, String phone, Role role, LocalDateTime createdAt,
                              boolean mfaEnabled, String termsVersion, LocalDateTime termsAcceptedAt) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCreatedAt(),
                u.isMfaEnabled(), u.getTermsVersion(), u.getTermsAcceptedAt());
    }
}
