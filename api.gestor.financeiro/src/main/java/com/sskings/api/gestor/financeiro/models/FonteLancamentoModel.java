package com.sskings.api.gestor.financeiro.models;

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
    private String fonte;

}
