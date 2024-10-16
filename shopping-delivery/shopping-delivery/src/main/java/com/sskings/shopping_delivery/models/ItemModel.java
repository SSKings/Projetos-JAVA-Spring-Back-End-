package com.sskings.shopping_delivery.models;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@EqualsAndHashCode(of = "id")
@Getter
@Setter
@Entity
@Table(name = "item")
public class ItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private String urlImage;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private Integer estoque;

    @Column(name = "preco_unitario",nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

}
