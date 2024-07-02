package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@DiscriminatorValue("SALDO")
@NoArgsConstructor
@Getter
@Setter
public class ContaLancamentoModel extends LancamentoModel {

    @ManyToOne
    @JoinColumn(name = "conta_id")
    private ContaModel conta;
}

