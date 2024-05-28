package com.sskings.api.gestor.financeiro.models;

import com.sskings.api.gestor.financeiro.dto.CartaoRequestDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "cartao_credito")
@Table(name = "cartao_credito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartaoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private long numero;

    @Column(nullable = false)
    private String banco;

    @Column(nullable = false)
    @DateTimeFormat(pattern = "MM:yy")
    private LocalDate vencimento;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal limite;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal limite_disponivel;

    public CartaoModel(CartaoRequestDto cartaoRequestDto){
        this.numero = cartaoRequestDto.numero();
        this.banco = cartaoRequestDto.banco();
        this.vencimento = cartaoRequestDto.vencimento();
        this.limite = cartaoRequestDto.limite();
        this.limite_disponivel = cartaoRequestDto.limite();
    }
}
