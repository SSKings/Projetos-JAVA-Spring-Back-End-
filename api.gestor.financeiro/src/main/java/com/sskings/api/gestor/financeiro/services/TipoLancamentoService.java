package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoResponseDto;
import com.sskings.api.gestor.financeiro.exception.RegraNegocioException;
import com.sskings.api.gestor.financeiro.models.TipoLancamentoModel;
import com.sskings.api.gestor.financeiro.repositories.TipoLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TipoLancamentoService {

    private final TipoLancamentoRepository tipoLancamentoRepository;

    public TipoResponseDto save(TipoRequestDto tipoRequestDto){
        TipoLancamentoModel tipo = new TipoLancamentoModel(tipoRequestDto);
        tipoLancamentoRepository.save(tipo);
        return new TipoResponseDto(tipo);
    }

    public Optional<TipoLancamentoModel> findById(UUID id){
        TipoLancamentoModel tipo = tipoLancamentoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Esse tipo de lançamento não existe."));
        return Optional.of(tipo);
    }

    public List<TipoLancamentoModel> findAll(){
        return tipoLancamentoRepository.findAll();
    }

    public void deleteById(UUID id){
        tipoLancamentoRepository.findById(id)
            .orElseThrow(() -> new RegraNegocioException("Esse tipo de lançamento não existe."));
        tipoLancamentoRepository.deleteById(id);
    }

}
