package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoResponseDto;
import com.sskings.api.gestor.financeiro.exception.RegraNegocioException;
import com.sskings.api.gestor.financeiro.models.*;
import com.sskings.api.gestor.financeiro.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LancamentoService {

    public final UsuarioRepository usuarioRepository;
    public final CartaoRepository cartaoRepository;
    public final ContaRepository contaRepository;
    public final TipoLancamentoRepository tipoLancamentoRepository;
    public final FonteLancamentoRepository fonteLancamentoRepository;
    public  final LancamentoRepository lancamentoRepository;

    @Transactional
    public LancamentoResponseDto save(LancamentoRequestDto lancamentoRequestDto) {
        UsuarioModel usuarioModel = usuarioRepository.findById(lancamentoRequestDto.usuario_id())
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado."));
        TipoLancamentoModel tipo = tipoLancamentoRepository.findById(lancamentoRequestDto.tipo_id())
                .orElseThrow(() -> new RegraNegocioException("Tipo de lançamento não encontrado."));
        FonteLancamentoModel fonte = fonteLancamentoRepository.findById(lancamentoRequestDto.fonte_id())
                .orElseThrow(() -> new RegraNegocioException("Fonte do lançamento não encontrado."));


        if (lancamentoRequestDto.conta_id() != null && lancamentoRequestDto.cartao_id() == null) {
            Optional<ContaModel> contaOptional = contaRepository.findById(lancamentoRequestDto.conta_id());
            if(contaOptional.isPresent()){
                ContaLancamentoModel contaLancamento = new ContaLancamentoModel();
                contaLancamento.setUsuario(usuarioModel);
                contaLancamento.setValor(lancamentoRequestDto.valor());
                contaLancamento.setTipo(tipo);
                contaLancamento.setFonte(fonte);
                contaLancamento.setConta(contaOptional.get());
                contaLancamento.setDataLancamento(LocalDate.now());
                lancamentoRepository.save((LancamentoModel) contaLancamento);
                return new LancamentoResponseDto(
                        contaLancamento.getValor(),contaLancamento.getUsuario(),contaLancamento.getTipo(),
                        contaLancamento.getFonte(),contaLancamento.getDataLancamento(),null,contaLancamento.getConta()
                );
            }else{
                throw new RegraNegocioException("Conta não encontrada");
            }


        } else if (lancamentoRequestDto.cartao_id() != null && lancamentoRequestDto.conta_id() == null) {
            Optional<CartaoModel> cartaoOptional = cartaoRepository.findById(lancamentoRequestDto.cartao_id());
            if(cartaoOptional.isPresent()){
                CartaoLancamentoModel cartaoLancamento = new CartaoLancamentoModel();
                cartaoLancamento.setValor(lancamentoRequestDto.valor());
                cartaoLancamento.setUsuario(usuarioModel);
                cartaoLancamento.setTipo(tipo);
                cartaoLancamento.setFonte(fonte);
                cartaoLancamento.setCartao(cartaoOptional.get());
                cartaoLancamento.setDataLancamento(LocalDate.now());
                lancamentoRepository.save((LancamentoModel) cartaoLancamento);
                return new LancamentoResponseDto(
                        cartaoLancamento.getValor(),cartaoLancamento.getUsuario(),cartaoLancamento.getTipo(),
                        cartaoLancamento.getFonte(),cartaoLancamento.getDataLancamento(),cartaoLancamento.getCartao(), null
                );

            }else {
                throw new RegraNegocioException("Cartão não encontrado.");
            }

        } else {
            throw new RegraNegocioException("Um lancamento deve ser feito com ou um cartão ou uma conta.");
        }
    }
    @Transactional
    public void delete(LancamentoModel lancamentoModel){
        lancamentoRepository.findById(lancamentoModel.getId())
                .orElseThrow(() -> new RegraNegocioException("Lançamento não encontrado"));
        lancamentoRepository.delete(lancamentoModel);
    }



}