package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "conta")
@Table(name = "conta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true)
    private long numero;

    @Column(nullable = false)
    private String banco;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal saldo;
}
