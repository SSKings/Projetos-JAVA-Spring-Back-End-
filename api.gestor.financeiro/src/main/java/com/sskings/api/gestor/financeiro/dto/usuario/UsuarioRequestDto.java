package com.sskings.api.gestor.financeiro.dto.usuario;

import com.sskings.api.gestor.financeiro.models.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioRequestDto(
        @NotBlank(message = "Campo username é obrigatório.")
        String username,
        @NotBlank(message = "Campo password é obrigatório.")
        String password,
        UserRole role,
        @NotBlank(message = "Campo e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        String email)
{

}
