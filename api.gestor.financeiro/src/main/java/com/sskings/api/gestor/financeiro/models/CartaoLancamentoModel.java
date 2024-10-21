package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private CartaoModel cartao;

    public CartaoLancamentoModel(UUID id, BigDecimal valor, LocalDate data, UsuarioModel usuario, TipoLancamentoModel tipo, FonteLancamentoModel fonte, CartaoModel cartao){
        super(id,valor,data,usuario,tipo,fonte);
        this.cartao = cartao;
    }
}
