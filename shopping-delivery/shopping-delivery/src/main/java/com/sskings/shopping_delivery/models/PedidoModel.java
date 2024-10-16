package com.sskings.shopping_delivery.models;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(of = "id")
@Getter
@Setter
@Entity
@Table(name = "pedido")
public class PedidoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private ClienteModel cliente;

    @ManyToOne
    @JoinColumn(name = "endereco_entrega_id", nullable = false)
    private EnderecoModel endereco;

    @Column(name = "data_pedido", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime dataPedido;

    @Column( name = "status",nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'PENDENTE'")
    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    @Column(nullable = false, precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal total;

    @OneToMany(mappedBy = "pedido",cascade = CascadeType.ALL)
    private List<ItemPedidoModel> itens;

}
