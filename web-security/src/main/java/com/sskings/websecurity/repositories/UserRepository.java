package com.sskings.websecurity.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import com.sskings.websecurity.models.User;

public interface UserRepository extends JpaRepository<User, String>{
	UserDetails findByUsername(String username);
}
