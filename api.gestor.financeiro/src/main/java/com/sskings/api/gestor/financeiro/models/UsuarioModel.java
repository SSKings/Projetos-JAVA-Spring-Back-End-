package com.sskings.api.gestor.financeiro.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioRequestDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(length = 50, nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    @EqualsAndHashCode.Include
    private String email;

    @Column(name = "data_cadastro", nullable = false)
    private LocalDate dataCadastro;

    @JsonIgnore
    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Set<CartaoModel> cartoes;

    @JsonIgnore
    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Set<ContaModel> contas;

    public UsuarioModel(UsuarioRequestDto usuarioRequestDto) {
        this.nome = usuarioRequestDto.nome();
        this.email = usuarioRequestDto.email();
    }
}
