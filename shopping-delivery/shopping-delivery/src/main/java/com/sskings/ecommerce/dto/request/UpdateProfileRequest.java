package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correção de dados (LGPD art. 18, III). Troca de email exigiria verificação e fica fora daqui. */
public record UpdateProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must have at most 100 characters") String name,
        @Size(max = 20, message = "Phone must have at most 20 characters") String phone
) {}
