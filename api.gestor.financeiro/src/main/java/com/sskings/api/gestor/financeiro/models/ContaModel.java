package com.sskings.api.gestor.financeiro.models;

import com.sskings.api.gestor.financeiro.dto.conta.ContaRequestDto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "conta")
@Table(name = "conta")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
public class ContaModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private Long numero;

    @Column(nullable = false)
    private String banco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal saldo;

    public ContaModel(ContaRequestDto contaRequestDto){
        this.numero = contaRequestDto.numero();
        this.banco = contaRequestDto.banco();
        this.saldo = contaRequestDto.saldo();
    }
}
