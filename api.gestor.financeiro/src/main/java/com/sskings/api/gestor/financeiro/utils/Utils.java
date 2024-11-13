package com.sskings.api.gestor.financeiro.utils;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoResponseDto;
import com.sskings.api.gestor.financeiro.models.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class Utils {

    public static Set<CartaoResponseDto> convertCartoes(Set<CartaoModel> cartoes){
        if(CollectionUtils.isEmpty(cartoes)){
            return Collections.emptySet();
        }
        return cartoes.stream()
                .map(CartaoResponseDto::new).collect(Collectors.toSet());
    }

    public static Set<ContaResponseDto> convertContas(Set<ContaModel> contas){
        if(CollectionUtils.isEmpty(contas)){
            return  Collections.emptySet();
        }
        return contas.stream()
                .map(ContaResponseDto::new).collect(Collectors.toSet());
    }

    public static Set<LancamentoResponseDto> converterLancamentos(Set<LancamentoModel> lancamentos) {
        if (CollectionUtils.isEmpty(lancamentos)) {
            return Collections.emptySet();
        }

        return lancamentos.stream()
                .map(Utils::mapearLancamentoParaDto)
                .collect(Collectors.toSet());
    }

    public static LancamentoResponseDto mapearLancamentoParaDto(LancamentoModel lancamentoModel) {
        LancamentoResponseDto.LancamentoResponseDtoBuilder builder = LancamentoResponseDto.builder()
                .usuario(lancamentoModel.getUsuario().getUsername())
                .dataLancamento(lancamentoModel.getDataLancamento())
                .tipo(lancamentoModel.getTipo().getNome())
                .fonte(lancamentoModel.getFonte().getNome())
                .valor(lancamentoModel.getValor());

        if (lancamentoModel instanceof CartaoLancamentoModel) {
            builder.cartao(((CartaoLancamentoModel) lancamentoModel).getCartao().getNumero());
        } else if (lancamentoModel instanceof ContaLancamentoModel) {
            builder.conta(((ContaLancamentoModel) lancamentoModel).getConta().getNumero());
        }

        return builder.build();
    }


}
