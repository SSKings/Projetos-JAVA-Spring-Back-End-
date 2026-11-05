package com.sskings.ecommerce.dto.request;

import com.sskings.ecommerce.model.enums.Role;

/** Campos nulos são ignorados (atualização parcial). */
public record UpdateUserRequest(Boolean active, Role role) {}
