package com.sskings.ecommerce.config;

import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** Beans de configuração que os testes @WebMvcTest precisam (no app real vêm de PaymentConfig). */
@TestConfiguration
@EnableConfigurationProperties({SecurityProperties.class, CorsProperties.class, PixProperties.class})
public class WebMvcTestConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
