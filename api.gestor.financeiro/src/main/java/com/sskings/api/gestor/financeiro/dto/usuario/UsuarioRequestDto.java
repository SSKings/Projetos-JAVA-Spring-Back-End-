package com.sskings.api.gestor.financeiro.dto.usuario;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioRequestDto(
        @NotBlank(message = "Campo nome é obrigatório.")
        String nome,
        @NotBlank(message = "Campo e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        String email)
{

}
