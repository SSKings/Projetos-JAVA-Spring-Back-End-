package com.sskings.ecommerce.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do recebedor Pix (app.pix.*). Validada no startup: a aplicação não sobe com uma chave inválida.
 *
 * @param key               chave Pix da loja: email, CPF/CNPJ (só dígitos), telefone (+55...) ou aleatória (EVP)
 * @param merchantName      nome do recebedor exibido no app do banco (até 25 caracteres)
 * @param merchantCity      cidade do recebedor (até 15 caracteres)
 * @param expirationMinutes validade do QR Code; depois disso o pedido é cancelado e o estoque devolvido
 * @param webhookSecret     segredo do HMAC do webhook; vazio = webhook desativado
 * @param webhookAllowedIps IPs/CIDRs do PSP autorizados a chamar o webhook; vazio = qualquer IP
 */
@Validated
@ConfigurationProperties(prefix = "app.pix")
public record PixProperties(
        @NotBlank
        @Pattern(regexp = "^([^\\s@]+@[^\\s@]+\\.[^\\s@]+|\\d{11}|\\d{14}|\\+55\\d{10,11}|"
                + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$",
                message = "app.pix.key must be an email, CPF, CNPJ, +55 phone or random (EVP) key")
        String key,
        @NotBlank String merchantName,
        @NotBlank String merchantCity,
        @Min(1) int expirationMinutes,
        String webhookSecret,
        java.util.List<String> webhookAllowedIps
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public PixProperties {
        webhookAllowedIps = webhookAllowedIps == null ? java.util.List.of() : webhookAllowedIps;
    }

    public PixProperties(String key, String merchantName, String merchantCity, int expirationMinutes, String webhookSecret) {
        this(key, merchantName, merchantCity, expirationMinutes, webhookSecret, java.util.List.of());
    }
}
