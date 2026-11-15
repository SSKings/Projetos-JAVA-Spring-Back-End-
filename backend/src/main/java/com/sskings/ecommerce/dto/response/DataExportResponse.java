package com.sskings.ecommerce.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Portabilidade e acesso (LGPD art. 18, II e V): tudo o que a loja guarda sobre o titular, em JSON. */
public record DataExportResponse(
        LocalDateTime generatedAt,
        ProfileResponse profile,
        List<AddressResponse> addresses,
        List<OrderResponse> orders,
        List<SecurityEvent> securityEvents
) {
    public record SecurityEvent(LocalDateTime at, String action, String ip) {}
}
