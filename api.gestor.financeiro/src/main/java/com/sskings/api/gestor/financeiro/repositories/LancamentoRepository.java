package com.sskings.api.gestor.financeiro.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LancamentoRepository extends JpaRepository<LancamentoRepository, UUID> {
}
