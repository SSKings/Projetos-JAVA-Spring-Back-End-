package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Os limites de tamanho espelham as colunas da tabela addresses (V1__init.sql). */
public record AddressRequest(
        @NotBlank(message = "Zip code is required") @Size(max = 10, message = "Zip code must have at most 10 characters") String zipCode,
        @NotBlank(message = "Street is required") @Size(max = 150, message = "Street must have at most 150 characters") String street,
        @Size(max = 10, message = "Number must have at most 10 characters") String number,
        @Size(max = 100, message = "Complement must have at most 100 characters") String complement,
        @NotBlank(message = "Neighborhood is required") @Size(max = 100, message = "Neighborhood must have at most 100 characters") String neighborhood,
        @NotBlank(message = "City is required") @Size(max = 100, message = "City must have at most 100 characters") String city,
        @NotBlank(message = "State is required") @Size(min = 2, max = 2, message = "State must have 2 letters") String state
) {}
