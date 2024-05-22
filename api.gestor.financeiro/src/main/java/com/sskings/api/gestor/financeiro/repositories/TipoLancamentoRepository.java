package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.TipoLancamentoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TipoLancamentoRepository extends JpaRepository<TipoLancamentoModel, UUID> {
}
