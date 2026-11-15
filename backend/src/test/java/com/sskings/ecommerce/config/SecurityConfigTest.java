package com.sskings.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    @DisplayName("Ferramentas de dev: só loopback (IPv4/IPv6) conta como local; nada de DNS nem endereços inválidos")
    void isLoopback() {
        assertThat(SecurityConfig.isLoopback("127.0.0.1")).isTrue();
        assertThat(SecurityConfig.isLoopback("127.10.0.1")).isTrue();
        assertThat(SecurityConfig.isLoopback("::1")).isTrue();
        assertThat(SecurityConfig.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
        for (String other : new String[]{"192.168.1.50", "10.0.0.1", "::ffff:192.168.1.5", "localhost", "", null}) {
            assertThat(SecurityConfig.isLoopback(other)).as(String.valueOf(other)).isFalse();
        }
    }

    @Test
    @DisplayName("Console H2: casa o caminho e o que vem abaixo dele, não prefixos parecidos")
    void underPath() {
        var matcher = SecurityConfig.underPath("/h2-console/");
        for (String ok : new String[]{"/h2-console", "/h2-console/", "/h2-console/login.do"}) {
            assertThat(matcher.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", ok))).as(ok).isTrue();
        }
        // revisão 6 (R6-4): o Tomcat entrega "/h2-%63onsole/" ao console; a trava tem que reconhecer o caminho também
        for (String encoded : new String[]{"/h2-%63onsole/", "/%682-console/login.do", "/h2-console;x=1/"}) {
            assertThat(matcher.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", encoded))).as(encoded).isTrue();
        }
        assertThat(matcher.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", "/h2-consolex"))).isFalse();
        assertThat(matcher.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/products"))).isFalse();
    }
}
