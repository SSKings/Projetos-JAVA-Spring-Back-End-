package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Reautenticação para ações sensíveis (excluir conta, desativar MFA). */
public record PasswordConfirmationRequest(
        @NotBlank @Size(max = 72) String password,
        @Pattern(regexp = "\\d{6}", message = "MFA code must have 6 digits") String mfaCode
) {}
