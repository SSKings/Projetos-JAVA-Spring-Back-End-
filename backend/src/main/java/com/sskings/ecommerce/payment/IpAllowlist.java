package com.sskings.ecommerce.payment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Lista de IPs/CIDRs (IPv4 e IPv6) permitidos. Lista vazia = sem restrição. */
public final class IpAllowlist {

    private final List<String> entries;

    public IpAllowlist(List<String> entries) {
        this.entries = entries == null ? List.of()
                : entries.stream().map(String::trim).filter(e -> !e.isEmpty()).toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean allows(String ip) {
        if (entries.isEmpty()) return true;
        if (ip == null) return false;
        return entries.stream().anyMatch(entry -> matches(entry, ip));
    }

    static boolean matches(String entry, String ip) {
        try {
            String[] parts = entry.split("/");
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (network.length != address.length) return false;
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : network.length * 8;
            for (int i = 0; i < network.length; i++) {
                int bits = Math.min(8, Math.max(0, prefix - i * 8));
                int mask = bits == 0 ? 0 : (0xFF << (8 - bits)) & 0xFF;
                if ((network[i] & mask) != (address[i] & mask)) return false;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}
