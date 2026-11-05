package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;

/** mfaSetupRequired: admin que precisa ativar o MFA antes de usar /api/admin/**. */
public record AuthResponse(String token, String name, String email, Role role, boolean mfaEnabled, boolean mfaSetupRequired) {
    public static AuthResponse of(String token, User user, boolean requireAdminMfa) {
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole(), user.isMfaEnabled(),
                requireAdminMfa && user.getRole() == Role.ADMIN && !user.isMfaEnabled());
    }
}
