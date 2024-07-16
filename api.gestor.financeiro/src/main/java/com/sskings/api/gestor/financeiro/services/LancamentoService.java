package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoResponseDto;
import com.sskings.api.gestor.financeiro.exception.BadRequestException;
import com.sskings.api.gestor.financeiro.exception.NotFoundException;
import com.sskings.api.gestor.financeiro.models.*;
import com.sskings.api.gestor.financeiro.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
                .orElseThrow(() -> new BadRequestException("Usuário não encontrado."));
        TipoLancamentoModel tipo = tipoLancamentoRepository.findById(lancamentoRequestDto.tipo_id())
                .orElseThrow(() -> new BadRequestException("Tipo de lançamento não encontrado."));
        FonteLancamentoModel fonte = fonteLancamentoRepository.findById(lancamentoRequestDto.fonte_id())
                .orElseThrow(() -> new BadRequestException("Fonte do lançamento não encontrado."));

        if (isContaLancamento(lancamentoRequestDto)) {
            return processContaLancamento(lancamentoRequestDto, usuarioModel, tipo, fonte);
        } else if (isCartaoLancamento(lancamentoRequestDto)) {
            return processCartaoLancamento(lancamentoRequestDto, usuarioModel, tipo, fonte);
        } else {
            throw new BadRequestException("Um lancamento deve ser feito com ou um cartão ou uma conta.");
        }
    }

    public List<LancamentoResponseDto> findAll(){
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findAll().stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()) {
            throw new NotFoundException("não há lancamentos.");
        }
        return lancamentos;
    }

    public List<LancamentoResponseDto> findByUsuarioId(UUID id){
        usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findByUsuarioId(id).stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()){
            throw new NotFoundException("não há lancamentos do usuário.");
        }
        return lancamentos;

    }

    public List<LancamentoResponseDto> findByUsuarioIdAndFonteNomeIgnoreCase(UUID id, String fonte){
        usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findByUsuarioIdAndFonteNomeIgnoreCase(id ,fonte).stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()){
            throw new NotFoundException("não há lancamentos dessa fonte: " + fonte);
        }
        return lancamentos;
    }

    public List<LancamentoResponseDto> findByUsuarioIdAndTipoNomeIgnoreCase(UUID id, String tipo){
        usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findByUsuarioIdAndTipoNomeIgnoreCase(id ,tipo).stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()){
            throw new NotFoundException("não há lancamentos do tipo: " + tipo);
        }
        return lancamentos;
    }

    public List<LancamentoResponseDto> findByUsuarioIdAndDataLancamento(UUID id, LocalDate data){
        usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findByUsuarioIdAndDataLancamento(id, data).stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()){
            throw new NotFoundException("não há lancamentos para data : " + data);
        }
        return lancamentos;
    }

    public List<LancamentoResponseDto> findByUsuarioIdAndValor(UUID id, BigDecimal valor){
        usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        List<LancamentoResponseDto> lancamentos = lancamentoRepository.findByUsuarioIdAndValor(id, valor).stream()
                .map(this::convertToDto).toList();
        if (lancamentos.isEmpty()){
            throw new NotFoundException("não há lancamentos com o valor: " + valor);
        }
        return lancamentos;
    }

    @Transactional
    public void deleteById(UUID id){
        lancamentoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lançamento não encontrado"));
        lancamentoRepository.deleteById(id);
    }

    private boolean isContaLancamento(LancamentoRequestDto lancamentoRequestDto){
        return lancamentoRequestDto.conta_id() != null && lancamentoRequestDto.cartao_id() == null;
    }

    private boolean isCartaoLancamento(LancamentoRequestDto lancamentoRequestDto){
        return lancamentoRequestDto.cartao_id() != null && lancamentoRequestDto.conta_id() == null;
    }

    private LancamentoResponseDto processContaLancamento(LancamentoRequestDto lancamentoRequestDto, UsuarioModel usuario,
                                                         TipoLancamentoModel tipo, FonteLancamentoModel fonte){
        ContaModel conta = contaRepository.findById(lancamentoRequestDto.conta_id())
                .orElseThrow(() -> new BadRequestException("Conta inexistente"));
        ContaLancamentoModel contaLancamentoModel = new ContaLancamentoModel();
        contaLancamentoModel.setConta(conta);
        contaLancamentoModel.setUsuario(usuario);
        contaLancamentoModel.setValor(lancamentoRequestDto.valor());
        contaLancamentoModel.setTipo(tipo);
        contaLancamentoModel.setFonte(fonte);
        contaLancamentoModel.setDataLancamento(LocalDate.now());
        lancamentoRepository.save(contaLancamentoModel);
        return new LancamentoResponseDto(
                contaLancamentoModel.getValor(),contaLancamentoModel.getUsuario().getUsername(),contaLancamentoModel.getTipo().getNome(),
                contaLancamentoModel.getFonte().getNome(),contaLancamentoModel.getDataLancamento(),null,contaLancamentoModel.getConta().getNumero()
        );
    }

    private LancamentoResponseDto processCartaoLancamento(LancamentoRequestDto lancamentoRequestDto, UsuarioModel usuario,
                                                          TipoLancamentoModel tipo, FonteLancamentoModel fonte){
        CartaoModel cartao = cartaoRepository.findById(lancamentoRequestDto.cartao_id())
                .orElseThrow(() -> new BadRequestException("Cartão inexistente"));
        CartaoLancamentoModel cartaoLancamentoModel = new CartaoLancamentoModel();
        cartaoLancamentoModel.setCartao(cartao);
        cartaoLancamentoModel.setUsuario(usuario);
        cartaoLancamentoModel.setValor(lancamentoRequestDto.valor());
        cartaoLancamentoModel.setTipo(tipo);
        cartaoLancamentoModel.setFonte(fonte);
        cartaoLancamentoModel.setDataLancamento(LocalDate.now());
        lancamentoRepository.save(cartaoLancamentoModel);
        return new LancamentoResponseDto(
                cartaoLancamentoModel.getValor(),cartaoLancamentoModel.getUsuario().getUsername(),cartaoLancamentoModel.getTipo().getNome(),
                cartaoLancamentoModel.getFonte().getNome(),cartaoLancamentoModel.getDataLancamento(),cartaoLancamentoModel.getCartao().getNumero(), null
        );


    }

    private LancamentoResponseDto convertToDto(LancamentoModel lancamento){
        Long conta = null;
        Long cartao = null;

        if (lancamento instanceof ContaLancamentoModel){
            conta = ((ContaLancamentoModel) lancamento).getConta().getNumero();
        }
        else if (lancamento instanceof CartaoLancamentoModel){
            cartao = ((CartaoLancamentoModel) lancamento).getCartao().getNumero();
        }
        return new LancamentoResponseDto(lancamento.getValor(),lancamento.getUsuario().getUsername(),
                lancamento.getTipo().getNome(), lancamento.getFonte().getNome(), lancamento.getDataLancamento(),
                cartao, conta);
    }
}