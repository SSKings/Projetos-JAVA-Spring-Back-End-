package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** mfaCode só é exigido de quem ativou o MFA (TOTP de 6 dígitos). */
public record LoginRequest(
        @Email @NotBlank @Size(max = 150) String email,
        @NotBlank @Size(max = 72) String password,
        @Pattern(regexp = "\\d{6}", message = "MFA code must have 6 digits") String mfaCode
) {
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
