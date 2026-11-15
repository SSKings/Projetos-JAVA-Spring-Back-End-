package com.sskings.ecommerce.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Gera o BR Code do Pix ("Pix copia e cola") no padrão EMV QRCPS-MPM definido pelo Banco Central
 * (Manual de Padrões para Iniciação do Pix). Cada campo é ID (2 dígitos) + tamanho (2 dígitos) + valor,
 * e o payload termina com o CRC16-CCITT (poly 0x1021, init 0xFFFF) de tudo o que vem antes, incluindo "6304".
 */
public final class PixBrCode {

    static final int MAX_NAME = 25;
    static final int MAX_CITY = 15;
    static final int MAX_TXID = 25;
    static final int MAX_KEY = 77;

    private PixBrCode() {}

    public static String build(String key, String merchantName, String merchantCity,
                               BigDecimal amount, String txid, String description) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY) {
            throw new IllegalArgumentException("Invalid Pix key");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (txid == null || !txid.matches("[A-Za-z0-9]{1," + MAX_TXID + "}")) {
            throw new IllegalArgumentException("txid must have 1 to 25 alphanumeric characters");
        }

        String gui = field("00", "br.gov.bcb.pix") + field("01", key);
        String info = sanitize(description, 99 - gui.length() - 4);
        String account = gui + (info.isEmpty() ? "" : field("02", info));

        String payload = field("00", "01")                       // Payload Format Indicator
                + field("01", "12")                              // QR de uso único
                + field("26", account)                           // Merchant Account Information (Pix)
                + field("52", "0000")                            // Merchant Category Code
                + field("53", "986")                             // Moeda: BRL
                + field("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                + field("58", "BR")
                + field("59", required(sanitize(merchantName, MAX_NAME), "merchant name"))
                + field("60", required(sanitize(merchantCity, MAX_CITY), "merchant city"))
                + field("62", field("05", txid))                 // Additional Data: txid
                + "6304";
        return payload + crc16(payload);
    }

    static String field(String id, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > 99) throw new IllegalArgumentException("Field " + id + " longer than 99 bytes");
        return id + String.format("%02d", length) + value;
    }

    /** Remove acentos e caracteres fora de [A-Za-z0-9 ], que alguns apps de banco rejeitam. */
    static String sanitize(String value, int max) {
        if (value == null || max <= 0) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return ascii.length() > max ? ascii.substring(0, max).trim() : ascii;
    }

    private static String required(String value, String name) {
        if (value.isEmpty()) throw new IllegalArgumentException("Pix " + name + " is required");
        return value;
    }

    /** CRC16/CCITT-FALSE, em hexadecimal maiúsculo com 4 dígitos. */
    static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
