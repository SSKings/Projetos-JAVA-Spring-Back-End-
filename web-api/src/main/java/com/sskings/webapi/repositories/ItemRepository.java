package com.sskings.webapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sskings.webapi.models.Item;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long>{

}
