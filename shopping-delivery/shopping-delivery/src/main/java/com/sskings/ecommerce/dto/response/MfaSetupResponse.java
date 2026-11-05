package com.sskings.ecommerce.dto.response;

/** secret: para digitar no app; otpauthUri: conteúdo do QR Code. Mostrado uma única vez. */
public record MfaSetupResponse(String secret, String otpauthUri) {}
