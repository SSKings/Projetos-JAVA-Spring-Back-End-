package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.model.enums.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Campos nulos são ignorados (atualização parcial). password/mfaCode são do ADMIN QUE ESTÁ AGINDO e só são exigidos
 * em mudanças sensíveis — trocar o papel de alguém ou alterar uma conta de admin (revisão 4, R4-3): sem isso, um
 * token de admin roubado criava um admin permanente para o atacante.
 */
public record UpdateUserRequest(
        Boolean active,
        Role role,
        @Size(max = 72) String password,
        @Pattern(regexp = "\\d{6}", message = "MFA code must have 6 digits") String mfaCode
) {
    public UpdateUserRequest(Boolean active, Role role) {
        this(active, role, null, null);
    }
}
