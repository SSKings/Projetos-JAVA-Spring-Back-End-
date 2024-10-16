package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.ItemModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<ItemModel, Long> {
}
