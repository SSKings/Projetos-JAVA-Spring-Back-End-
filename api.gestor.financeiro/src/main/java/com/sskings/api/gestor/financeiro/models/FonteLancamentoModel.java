package com.sskings.api.gestor.financeiro.models;

import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteRequestDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity(name = "fonte_lancamento")
@Table(name = "fonte_lancamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FonteLancamentoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String nome;

    public FonteLancamentoModel(FonteRequestDto fonteRequestDto) {
        this.nome = fonteRequestDto.fonte();
    }
}
