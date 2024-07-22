package com.sskings.api.gestor.financeiro.dto.usuario;

import jakarta.validation.constraints.NotBlank;

public record AuthenticationDto(

        @NotBlank(message = "O campo username é obrigatorio")
        String username,
        @NotBlank(message = "O campo password é obrigatório")
        String password) {
}
