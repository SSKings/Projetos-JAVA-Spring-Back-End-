package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.PedidoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<PedidoModel, Long> {

    @Query("SELECT p FROM PedidoModel p JOIN FETCH p.itens WHERE p.id = :id")
    Optional<PedidoModel> findByIdWithItens(@Param("id") Long id);

    @Query("SELECT p FROM PedidoModel p LEFT JOIN FETCH p.itens WHERE p.cliente.id = :clienteId")
    List<PedidoModel> findByCliente_Id(@Param("clienteId") Long clienteId);
}
