package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.repositories.TipoLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TipoLancamentoService {

    private final TipoLancamentoRepository tipoLancamentoRepository;
}
