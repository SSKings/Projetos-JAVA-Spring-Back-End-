package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.repositories.FonteLancamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FonteLancamentoService {

    private final FonteLancamentoRepository fonteLancamentoRepository;
}
