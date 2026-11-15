package com.sskings.ecommerce.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpAllowlistTest {

    @Test
    @DisplayName("Lista vazia (ou nula) permite tudo")
    void empty() {
        assertThat(new IpAllowlist(List.of()).allows("1.2.3.4")).isTrue();
        assertThat(new IpAllowlist(null).isEmpty()).isTrue();
        assertThat(new IpAllowlist(List.of(" ", "")).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("IP exato, CIDR IPv4 (/32, /24, /20, /0) e IPv6")
    void matching() {
        IpAllowlist list = new IpAllowlist(List.of("200.10.20.30", "10.1.0.0/20", "2001:db8::/32"));
        assertThat(list.allows("200.10.20.30")).isTrue();
        assertThat(list.allows("200.10.20.31")).isFalse();
        assertThat(list.allows("10.1.15.255")).isTrue();
        assertThat(list.allows("10.1.16.0")).isFalse();
        assertThat(list.allows("2001:db8:1::5")).isTrue();
        assertThat(list.allows("2001:db9::1")).isFalse();
        assertThat(list.allows(null)).isFalse();
        assertThat(IpAllowlist.matches("0.0.0.0/0", "8.8.8.8")).isTrue();
        assertThat(IpAllowlist.matches("10.0.0.0/8", "::1")).isFalse();           // famílias diferentes
        assertThat(IpAllowlist.matches("10.0.0.0/x", "10.0.0.1")).isFalse();      // entrada inválida
        assertThat(IpAllowlist.matches("nao-e-ip.invalid", "10.0.0.1")).isFalse();
    }
}
