package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.FonteLancamentoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FonteLancamentoRepository extends JpaRepository<FonteLancamentoModel, UUID> {
}
