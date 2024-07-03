package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.LancamentoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LancamentoRepository extends JpaRepository<LancamentoModel, UUID> {

    List<LancamentoModel> findByUsuarioId(UUID id);
    List<LancamentoModel> findByUsuarioIdAndFonteNomeIgnoreCase(UUID id, String fonteNome);
    List<LancamentoModel> findByUsuarioIdAndTipoNomeIgnoreCase(UUID id, String tipoNome);
    List<LancamentoModel> findByUsuarioIdAndDataLancamento(UUID id, LocalDate dataLancamento);
}
