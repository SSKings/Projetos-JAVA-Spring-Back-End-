package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.dto.request.validation.BcryptSafe;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Troca de senha: exige a senha atual (e o código MFA, se ativo) e aplica a mesma política do cadastro. */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Password must contain letters and numbers")
        @BcryptSafe
        String newPassword,
        @Pattern(regexp = "\\d{6}", message = "MFA code must have 6 digits") String mfaCode
) {}
