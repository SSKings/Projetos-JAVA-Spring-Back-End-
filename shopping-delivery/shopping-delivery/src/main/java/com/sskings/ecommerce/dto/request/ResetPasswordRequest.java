package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.dto.request.validation.BcryptSafe;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nova senha pelo link do e-mail: mesma política do cadastro (formato aqui; senha comum/pessoal no serviço). */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 64) String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Password must contain letters and numbers")
        @BcryptSafe
        String newPassword
) {}
