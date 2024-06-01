package com.sskings.api.gestor.financeiro.models;

import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoRequestDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "lancamento")
@Table(name = "lancamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LancamentoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @ManyToOne
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoLancamentoModel tipo;

    @ManyToOne
    @JoinColumn(name = "fonte_id", nullable = false)
    private FonteLancamentoModel fonte;

    @ManyToOne
    @JoinColumn(name = "cartao_credito_id")
    private CartaoModel cartao;

    @ManyToOne
    @JoinColumn(name = "conta_id")
    private ContaModel conta;
}
