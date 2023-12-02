package com.sskings.webapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sskings.webapi.models.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long>{

}
