package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.PedidoModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<PedidoModel,Long> {
}
