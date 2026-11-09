package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.dto.request.validation.BcryptSafe;
import jakarta.validation.constraints.*;

/**
 * Os limites de tamanho espelham as colunas da tabela users; 72 bytes é o máximo que o BCrypt considera (@BcryptSafe).
 * A política de senha (8+ caracteres, letras e números) é aplicada aqui — não só no front-end.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must have at most 100 characters") String name,
        @Email(message = "Email is invalid") @NotBlank(message = "Email is required") @Size(max = 150, message = "Email must have at most 150 characters") String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Password must contain letters and numbers")
        @BcryptSafe
        String password,
        @Size(max = 20, message = "Phone must have at most 20 characters") String phone,
        @NotNull(message = "You must accept the terms of use and the privacy policy")
        @AssertTrue(message = "You must accept the terms of use and the privacy policy") Boolean acceptTerms
) {}
