package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteResponseDto;
import com.sskings.api.gestor.financeiro.exception.RegraNegocioException;
import com.sskings.api.gestor.financeiro.models.FonteLancamentoModel;
import com.sskings.api.gestor.financeiro.repositories.FonteLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FonteLancamentoService {

    private final FonteLancamentoRepository fonteLancamentoRepository;

    public FonteResponseDto save(FonteRequestDto fonteRequestDto){
        FonteLancamentoModel fonte = new FonteLancamentoModel(fonteRequestDto);
        fonteLancamentoRepository.save(fonte);
        return new FonteResponseDto(fonte);
    }

    public Optional<FonteLancamentoModel> findById(UUID id){
        FonteLancamentoModel fonte = fonteLancamentoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Fonte não encontrada."));
        return Optional.of(fonte);
    }

    public List<FonteLancamentoModel> findAll(){
        return fonteLancamentoRepository.findAll();
    }

    public void deleteById(UUID id){
        fonteLancamentoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Fonte não encontrada."));
        fonteLancamentoRepository.deleteById(id);
    }
}
