package com.sskings.ecommerce.config;

import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.payment.WebhookSignatureVerifier;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.SecurityProperties;
import com.sskings.ecommerce.security.TotpService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({PixProperties.class, SecurityProperties.class, CorsProperties.class})
public class PaymentConfig {

    /** Relógio injetável: os testes controlam "agora" para validar expiração e janela do webhook. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public CryptoService cryptoService(SecurityProperties props) {
        return new CryptoService(props.encryptionKey());
    }

    @Bean
    public TotpService totpService(Clock clock) {
        return new TotpService(clock);
    }

    @Bean
    public WebhookSignatureVerifier webhookSignatureVerifier(PixProperties pix, Clock clock) {
        return new WebhookSignatureVerifier(pix.webhookSecret(), clock);
    }
}
