package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoResponseDto;
import com.sskings.api.gestor.financeiro.exception.BadRequestException;
import com.sskings.api.gestor.financeiro.models.TipoLancamentoModel;
import com.sskings.api.gestor.financeiro.repositories.TipoLancamentoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TipoLancamentoService {

    private final TipoLancamentoRepository tipoLancamentoRepository;

    @Transactional
    public TipoResponseDto save(TipoRequestDto tipoRequestDto){
        TipoLancamentoModel tipo = new TipoLancamentoModel(tipoRequestDto);
        tipoLancamentoRepository.save(tipo);
        return new TipoResponseDto(tipo);
    }

    public Optional<TipoLancamentoModel> findById(UUID id){
        TipoLancamentoModel tipo = tipoLancamentoRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Esse tipo de lançamento não existe."));
        return Optional.of(tipo);
    }

    public List<TipoResponseDto> findAll(){
        return tipoLancamentoRepository.findAll()
                .stream()
                .map(TipoResponseDto::new)
                .toList();
    }

    @Transactional
    public void deleteById(UUID id){
        tipoLancamentoRepository.findById(id)
            .orElseThrow(() -> new BadRequestException("O tipo de lançamento não existe."));
        tipoLancamentoRepository.deleteById(id);
    }

}
