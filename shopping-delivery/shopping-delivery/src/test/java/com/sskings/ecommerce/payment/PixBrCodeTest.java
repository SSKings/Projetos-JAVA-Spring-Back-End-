package com.sskings.ecommerce.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixBrCodeTest {

    /** Lê um payload EMV (ID + tamanho + valor) para conferir campo a campo. */
    static Map<String, String> parse(String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 0;
        while (i < payload.length()) {
            String id = payload.substring(i, i + 2);
            int len = Integer.parseInt(payload.substring(i + 2, i + 4));
            fields.put(id, payload.substring(i + 4, i + 4 + len));
            i += 4 + len;
        }
        return fields;
    }

    @Test
    @DisplayName("CRC16/CCITT-FALSE: valor de verificação padrão e exemplo do manual do BR Code (Bacen)")
    void crc16_knownVectors() {
        assertThat(PixBrCode.crc16("123456789")).isEqualTo("29B1");
        // Exemplo de QR estático do Manual de Padrões para Iniciação do Pix — CRC esperado 1D3D
        String bacen = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
                + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304";
        assertThat(PixBrCode.crc16(bacen)).isEqualTo("1D3D");
    }

    @Test
    @DisplayName("Monta todos os campos obrigatórios com valor, txid e CRC válido")
    void build_fields() {
        String payload = PixBrCode.build("pix@loja.com", "Loja São João", "São Paulo",
                new BigDecimal("59.8"), "ABC123", "Pedido 42");

        Map<String, String> f = parse(payload);
        assertThat(f.get("00")).isEqualTo("01");
        assertThat(f.get("01")).isEqualTo("12");
        assertThat(parse(f.get("26"))).containsEntry("00", "br.gov.bcb.pix")
                .containsEntry("01", "pix@loja.com").containsEntry("02", "Pedido 42");
        assertThat(f.get("52")).isEqualTo("0000");
        assertThat(f.get("53")).isEqualTo("986");
        assertThat(f.get("54")).isEqualTo("59.80");
        assertThat(f.get("58")).isEqualTo("BR");
        assertThat(f.get("59")).isEqualTo("Loja Sao Joao");
        assertThat(f.get("60")).isEqualTo("Sao Paulo");
        assertThat(parse(f.get("62"))).containsEntry("05", "ABC123");
        String withoutCrc = payload.substring(0, payload.length() - 4);
        assertThat(withoutCrc).endsWith("6304");
        assertThat(f.get("63")).isEqualTo(PixBrCode.crc16(withoutCrc));
    }

    @Test
    @DisplayName("Nome e cidade são limitados a 25/15 caracteres e sem símbolos")
    void build_truncatesAndSanitizes() {
        Map<String, String> f = parse(PixBrCode.build("pix@loja.com", "Ação & Cia — Comércio Eletrônico Ltda",
                "Santo Antônio do Descoberto", BigDecimal.ONE, "T1", null));
        assertThat(f.get("59")).isEqualTo("Acao Cia Comercio Eletron").hasSizeLessThanOrEqualTo(25);
        assertThat(f.get("60")).isEqualTo("Santo Antonio d").hasSize(15);
        assertThat(parse(f.get("26"))).doesNotContainKey("02");
    }

    @Test
    @DisplayName("Chave longa: descrição é omitida para o campo 26 não passar de 99 bytes")
    void build_longKeyDropsDescription() {
        String key = "a".repeat(65) + "@loja.com.br";
        Map<String, String> f = parse(PixBrCode.build(key, "Loja", "SP", BigDecimal.TEN, "T1", "Pedido 1"));
        assertThat(f.get("26").length()).isLessThanOrEqualTo(99);
        assertThat(parse(f.get("26"))).doesNotContainKey("02");
    }

    @Test
    @DisplayName("Rejeita chave, valor, txid, nome e cidade inválidos")
    void build_invalidArguments() {
        BigDecimal one = BigDecimal.ONE;
        assertThatThrownBy(() -> PixBrCode.build(" ", "L", "SP", one, "T", null)).hasMessage("Invalid Pix key");
        assertThatThrownBy(() -> PixBrCode.build(null, "L", "SP", one, "T", null)).hasMessage("Invalid Pix key");
        assertThatThrownBy(() -> PixBrCode.build("k".repeat(78), "L", "SP", one, "T", null)).hasMessage("Invalid Pix key");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", "SP", BigDecimal.ZERO, "T", null)).hasMessage("Amount must be positive");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", "SP", null, "T", null)).hasMessage("Amount must be positive");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", "SP", one, "TX-1", null)).hasMessageContaining("txid");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", "SP", one, "T".repeat(26), null)).hasMessageContaining("txid");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", "SP", one, null, null)).hasMessageContaining("txid");
        assertThatThrownBy(() -> PixBrCode.build("k", "%%", "SP", one, "T", null)).hasMessage("Pix merchant name is required");
        assertThatThrownBy(() -> PixBrCode.build("k", "L", null, one, "T", null)).hasMessage("Pix merchant city is required");
        assertThatThrownBy(() -> PixBrCode.field("26", "x".repeat(100))).hasMessageContaining("longer than 99");
    }

    @Test
    @DisplayName("Valor é arredondado para 2 casas")
    void build_amountScale() {
        assertThat(parse(PixBrCode.build("k@k.com", "L", "SP", new BigDecimal("10.005"), "T", null)).get("54")).isEqualTo("10.01");
    }
}
