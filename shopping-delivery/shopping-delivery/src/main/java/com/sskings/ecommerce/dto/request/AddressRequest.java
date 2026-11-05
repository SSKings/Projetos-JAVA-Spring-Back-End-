package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank String zipCode,
        @NotBlank String street,
        String number,
        String complement,
        @NotBlank String neighborhood,
        @NotBlank String city,
        @NotBlank @Size(min = 2, max = 2) String state
) {}
