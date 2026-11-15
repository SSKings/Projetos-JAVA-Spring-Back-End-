package com.sskings.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * Caminho da requisição como o Spring MVC o enxerga: decodificado ({@code %66} vira {@code f}), sem
 * {@code ;parâmetros} e sem o context path. Filtro que decide por caminho (limite, trava de ferramenta) tem que usar
 * ESTE caminho, nunca {@code getRequestURI()} cru: {@code /api/auth/%66orgot-password} chega ao controller de
 * {@code /forgot-password}, mas não é igual a ele como texto (revisão 6, R6-3/R6-4).
 * <p>Barras e pontos codificados ({@code %2F}, {@code %2E}), {@code //} e {@code ..} o firewall do Spring Security já
 * recusa antes de qualquer filtro.
 */
public final class RequestPaths {

    private static final UrlPathHelper HELPER = new UrlPathHelper();   // decodifica e tira ";..." (padrões)

    private RequestPaths() {}

    public static String of(HttpServletRequest request) {
        return HELPER.getPathWithinApplication(request);
    }
}
