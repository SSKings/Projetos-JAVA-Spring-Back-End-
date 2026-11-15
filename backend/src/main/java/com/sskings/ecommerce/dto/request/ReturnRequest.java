package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.Size;

/** O motivo é opcional: o CDC (art. 49) não exige justificativa para o arrependimento. */
public record ReturnRequest(@Size(max = 500, message = "Reason must have at most 500 characters") String reason) {}
