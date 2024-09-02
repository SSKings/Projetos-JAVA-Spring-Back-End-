package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@DiscriminatorValue("CREDITO")
@Getter
@Setter
@NoArgsConstructor
public class CartaoLancamentoModel extends LancamentoModel{

    @ManyToOne
    @JoinColumn(name = "cartao_id")
    private CartaoModel cartao;

    public CartaoLancamentoModel(UUID id, BigDecimal valor, LocalDate data, UsuarioModel usuario, TipoLancamentoModel tipo, FonteLancamentoModel fonte, CartaoModel cartao){
        super(id,valor,data,usuario,tipo,fonte);
        this.cartao = cartao;
    }
}
