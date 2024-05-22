package com.sskings.api.gestor.financeiro.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity(name = "tipo_lancamento")
@Table(name = "tipo_lancamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipoLancamentoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String tipo;

}
