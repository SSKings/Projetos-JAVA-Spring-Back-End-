package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token que veio no link do e-mail (confirmar e-mail). */
public record EmailTokenRequest(@NotBlank @Size(max = 64) String token) {}
