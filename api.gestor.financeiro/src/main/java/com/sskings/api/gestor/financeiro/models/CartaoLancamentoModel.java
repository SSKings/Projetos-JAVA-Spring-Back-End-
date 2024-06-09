package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("credito")
@NoArgsConstructor
@Getter
@Setter
public class CartaoLancamentoModel extends LancamentoModel{

    @ManyToOne
    @JoinColumn(name = "cartao_id")
    private CartaoModel cartao;
}
