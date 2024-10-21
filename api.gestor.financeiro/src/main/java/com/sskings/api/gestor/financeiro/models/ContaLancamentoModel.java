package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


@Entity
@DiscriminatorValue("SALDO")
@Getter
@Setter
public class ContaLancamentoModel extends LancamentoModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private ContaModel conta;

    public ContaLancamentoModel() {
        super();

    }

    public ContaLancamentoModel(UUID id, BigDecimal valor, LocalDate data, UsuarioModel usuario, TipoLancamentoModel tipo, FonteLancamentoModel fonte, ContaModel conta){
        super(id,valor,data,usuario,tipo,fonte);
        this.conta = conta;
    }
}

