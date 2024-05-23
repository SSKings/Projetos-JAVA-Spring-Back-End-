package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.LancamentoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LancamentoRepository extends JpaRepository<LancamentoModel, UUID> {
}
