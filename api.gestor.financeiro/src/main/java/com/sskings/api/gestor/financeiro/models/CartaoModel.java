package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "cartao_credito")
@Table(name = "cartao_credito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartaoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false ,unique = true)
    private long numero;

    @Column(nullable = false)
    private String banco;

    @Column(nullable = false)
    private LocalDate vencimento;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal limite;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal limite_disponivel;
}
