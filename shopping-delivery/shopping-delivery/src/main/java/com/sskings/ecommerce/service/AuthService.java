package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already in use");
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .build();
        userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.of(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new BusinessException("Account is blocked");
        }
        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.of(token, user);
    }
}
