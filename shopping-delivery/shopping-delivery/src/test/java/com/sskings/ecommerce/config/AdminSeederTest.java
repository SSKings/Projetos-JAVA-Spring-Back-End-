package com.sskings.ecommerce.config;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AdminSeeder seeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "name", "Admin");
        ReflectionTestUtils.setField(seeder, "email", "admin@x.com");
        ReflectionTestUtils.setField(seeder, "password", "secret");
    }

    @Test
    @DisplayName("Cria o admin quando ele ainda não existe")
    void run_createsAdmin() {
        given(userRepository.existsByEmail("admin@x.com")).willReturn(false);
        given(passwordEncoder.encode("secret")).willReturn("hashed");

        seeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("Não duplica o admin em reinicializações")
    void run_adminExists_doesNothing() {
        given(userRepository.existsByEmail("admin@x.com")).willReturn(true);

        seeder.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sem ADMIN_PASSWORD (dev), gera senha aleatória forte em vez de usar um valor público")
    void run_withoutPassword_generatesRandom() {
        ReflectionTestUtils.setField(seeder, "password", " ");
        given(userRepository.existsByEmail("admin@x.com")).willReturn(false);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        given(passwordEncoder.encode(raw.capture())).willReturn("hashed");

        seeder.run();

        assertThat(raw.getValue()).hasSize(16).matches(".*\\p{L}.*").matches(".*\\d.*").isNotEqualTo("admin123");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("Senhas geradas não se repetem")
    void generatePassword_unique() {
        assertThat(java.util.stream.IntStream.range(0, 100).mapToObj(i -> AdminSeeder.generatePassword()).distinct())
                .hasSize(100);
    }
}
