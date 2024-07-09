package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteResponseDto;
import com.sskings.api.gestor.financeiro.exception.NotFoundException;
import com.sskings.api.gestor.financeiro.models.FonteLancamentoModel;
import com.sskings.api.gestor.financeiro.repositories.FonteLancamentoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FonteLancamentoService {

    private final FonteLancamentoRepository fonteLancamentoRepository;

    @Transactional
    public FonteResponseDto save(FonteRequestDto fonteRequestDto){
        FonteLancamentoModel fonte = new FonteLancamentoModel(fonteRequestDto);
        fonteLancamentoRepository.save(fonte);
        return new FonteResponseDto(fonte);
    }

    public Optional<FonteLancamentoModel> findById(UUID id){
        FonteLancamentoModel fonte = fonteLancamentoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fonte não encontrada."));
        return Optional.of(fonte);
    }

    public List<FonteResponseDto> findAll(){
        return fonteLancamentoRepository.findAll()
                .stream()
                .map(FonteResponseDto::new)
                .toList();
    }

    @Transactional
    public void deleteById(UUID id){
        fonteLancamentoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fonte não encontrada."));
        fonteLancamentoRepository.deleteById(id);
    }


}
