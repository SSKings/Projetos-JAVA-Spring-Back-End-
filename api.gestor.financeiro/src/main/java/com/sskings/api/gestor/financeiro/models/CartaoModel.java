package com.sskings.api.gestor.financeiro.models;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoRequestDto;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cartao_credito")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
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

    @ManyToOne(fetch = FetchType.LAZY)
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
