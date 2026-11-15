package com.sskings.ecommerce.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.repository.EmailTokenRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * ADR-025: confirmação de e-mail, recuperação de senha e avisos, de ponta a ponta. O JavaMailSender é substituído
 * por um mock que guarda as mensagens: o teste lê o link de dentro do e-mail, como o cliente faria.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-flows;DB_CLOSE_DELAY=-1",
        "app.mail.enabled=true", "app.mail.async=false", "app.require-verified-email=true",
        "app.mail.public-url=https://loja.com.br"})
@AutoConfigureMockMvc
class EmailFlowsIntegrationTest {

    private static final String PASSWORD = "Vagalume4821";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]{43})");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired EmailTokenRepository tokens;
    @MockitoBean JavaMailSender mailSender;

    private static int counter = 0;

    private static synchronized String ip() {
        counter++;
        return "10.25." + (counter / 250) + "." + (counter % 250 + 1);
    }

    @BeforeEach
    void resetMail() {
        reset(mailSender);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        String ip = ip();
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private List<SimpleMailMessage> sent() {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private SimpleMailMessage lastTo(String email) {
        return sent().stream().filter(m -> m.getTo()[0].equals(email)).reduce((a, b) -> b).orElseThrow();
    }

    private static String tokenIn(SimpleMailMessage m) {
        Matcher matcher = TOKEN.matcher(m.getText());
        assertThat(matcher.find()).as("link com token no e-mail").isTrue();
        return matcher.group(1);
    }

    private String register(String email) throws Exception {
        var r = call(post("/api/auth/register"), null,
                Map.of("name", "Cliente Email", "email", email, "password", PASSWORD, "acceptTerms", true));
        assertThat(r.getStatus()).isEqualTo(201);
        return body(r).get("token").asText();
    }

    private static String email() {
        return "mail-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    @DisplayName("Cadastro manda o link; sem confirmar não fecha pedido; o link confirma uma vez só; o banco só tem o hash")
    void verificationGatesCheckout() throws Exception {
        String email = email();
        String jwt = register(email);
        assertThat(body(call(get("/api/account"), jwt, null)).get("emailVerified").asBoolean()).isFalse();

        SimpleMailMessage welcome = lastTo(email);
        assertThat(welcome.getSubject()).startsWith("Confirme seu e-mail");
        String token = tokenIn(welcome);
        assertThat(welcome.getText()).contains("https://loja.com.br/verificar-email#token=" + token);
        assertThat(tokens.findAll()).noneMatch(t -> t.getTokenHash().equals(token));   // só o SHA-256

        long address = body(call(post("/api/addresses"), jwt, Map.of("zipCode", "01000-000", "street", "Rua E",
                "number", "1", "neighborhood", "Centro", "city", "São Paulo", "state", "SP"))).get("id").asLong();
        call(post("/api/cart/items"), jwt, Map.of("productId", 1, "quantity", 1));
        var blocked = call(post("/api/orders/checkout"), jwt, Map.of("addressId", address));
        assertThat(blocked.getStatus()).isEqualTo(400);
        assertThat(body(blocked).get("message").asText()).isEqualTo("Confirm your email before placing an order");

        assertThat(call(post("/api/auth/verify-email"), null, Map.of("token", token)).getStatus()).isEqualTo(204);
        assertThat(call(post("/api/auth/verify-email"), null, Map.of("token", token)).getStatus()).isEqualTo(400);   // uso único
        assertThat(body(call(get("/api/account"), jwt, null)).get("emailVerified").asBoolean()).isTrue();

        var order = call(post("/api/orders/checkout"), jwt, Map.of("addressId", address));
        assertThat(order.getStatus()).isEqualTo(201);
        long orderId = body(order).get("id").asLong();
        SimpleMailMessage orderMail = lastTo(email);
        assertThat(orderMail.getSubject()).startsWith("Pedido #" + orderId + " recebido");
        assertThat(orderMail.getText()).contains(body(order).at("/payment/pixCopyPaste").asText(), "/orders/" + orderId);
    }

    @Test
    @DisplayName("Link inválido, de outro tipo ou malformado: mesma resposta; reenviar respeita o intervalo")
    void invalidLinksAndResend() throws Exception {
        String email = email();
        String jwt = register(email);
        String verifyToken = tokenIn(lastTo(email));
        for (String bad : new String[]{"x".repeat(43), "curto", verifyToken + "a"}) {
            var r = call(post("/api/auth/verify-email"), null, Map.of("token", bad));
            assertThat(r.getStatus()).as(bad).isEqualTo(400);
        }
        // token de confirmação não serve para redefinir senha
        var wrongPurpose = call(post("/api/auth/reset-password"), null, Map.of("token", verifyToken, "newPassword", "Outra98765x"));
        assertThat(body(wrongPurpose).get("message").asText()).isEqualTo("Invalid or expired link");

        var tooSoon = call(post("/api/account/verification-email"), jwt, null);
        assertThat(tooSoon.getStatus()).isEqualTo(400);
        assertThat(body(tooSoon).get("message").asText()).startsWith("Wait a few minutes");
    }

    @Test
    @DisplayName("Esqueci a senha: mesma resposta para conta inexistente; link redefine, derruba sessões, avisa e vale uma vez")
    void passwordReset() throws Exception {
        String email = email();
        String oldJwt = register(email);
        tokenIn(lastTo(email));
        reset(mailSender);

        assertThat(call(post("/api/auth/forgot-password"), null, Map.of("email", "ninguem-" + UUID.randomUUID() + "@teste.com"))
                .getStatus()).isEqualTo(202);
        assertThat(sent()).isEmpty();                                                   // não revela, não envia
        assertThat(call(post("/api/auth/forgot-password"), null, Map.of("email", email.toUpperCase())).getStatus())
                .isEqualTo(202);                                                        // e-mail normalizado
        String token = tokenIn(lastTo(email));
        assertThat(lastTo(email).getText()).contains("https://loja.com.br/redefinir-senha#token=" + token);

        call(post("/api/auth/forgot-password"), null, Map.of("email", email));         // 2º pedido em menos de 5 min
        assertThat(sent()).hasSize(1);                                                  // não lota a caixa de ninguém

        var weak = call(post("/api/auth/reset-password"), null, Map.of("token", token, "newPassword", "senha123"));
        assertThat(weak.getStatus()).isEqualTo(400);
        assertThat(body(weak).get("message").asText()).isEqualTo(com.sskings.ecommerce.security.PasswordStrength.TOO_COMMON);
        // senha fraca não queima o link: o mesmo link funciona com uma senha boa
        assertThat(call(post("/api/auth/reset-password"), null, Map.of("token", token, "newPassword", "Libelula5732x"))
                .getStatus()).isEqualTo(204);
        assertThat(call(get("/api/account"), oldJwt, null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Redefinir com link válido: senha nova vale, antiga não, sessões caem, link não serve de novo, e-mail confirmado")
    void passwordResetCompletes() throws Exception {
        String email = email();
        String oldJwt = register(email);
        reset(mailSender);
        call(post("/api/auth/forgot-password"), null, Map.of("email", email));
        String token = tokenIn(lastTo(email));

        assertThat(call(post("/api/auth/reset-password"), null, Map.of("token", token, "newPassword", "Libelula5732x"))
                .getStatus()).isEqualTo(204);
        assertThat(call(get("/api/account"), oldJwt, null).getStatus()).isEqualTo(401);
        assertThat(call(post("/api/auth/login"), null, Map.of("email", email, "password", PASSWORD)).getStatus()).isEqualTo(400);
        var login = call(post("/api/auth/login"), null, Map.of("email", email, "password", "Libelula5732x"));
        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(body(login).get("emailVerified").asBoolean()).isTrue();             // o link provou o e-mail
        assertThat(call(post("/api/auth/reset-password"), null, Map.of("token", token, "newPassword", "Outra98765x"))
                .getStatus()).isEqualTo(400);
        assertThat(lastTo(email).getSubject()).startsWith("Alerta de segurança: Sua senha foi redefinida");
    }

    @Test
    @DisplayName("Trocar a senha logado aposenta um link de nova senha pedido antes (quem tinha o e-mail não usa depois)")
    void passwordChangeInvalidatesPendingReset() throws Exception {
        String email = email();
        String jwt = register(email);
        reset(mailSender);
        call(post("/api/auth/forgot-password"), null, Map.of("email", email));
        String pending = tokenIn(lastTo(email));
        assertThat(call(post("/api/account/password"), jwt, Map.of("currentPassword", PASSWORD, "newPassword", "Libelula5732x"))
                .getStatus()).isEqualTo(200);
        var r = call(post("/api/auth/reset-password"), null, Map.of("token", pending, "newPassword", "Outra98765x"));
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r).get("message").asText()).isEqualTo("Invalid or expired link");
        assertThat(call(post("/api/auth/login"), null, Map.of("email", email, "password", "Libelula5732x")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Cadastro com o e-mail de outra pessoa e nome com link: o e-mail para ela não leva o link")
    void maliciousNameNotInEmail() throws Exception {
        String victim = email();
        call(post("/api/auth/register"), null, Map.of("name", "http://site-falso.example/entrar Silva", "email", victim,
                "password", PASSWORD, "acceptTerms", true));
        SimpleMailMessage m = lastTo(victim);
        assertThat(m.getText()).startsWith("Olá!").doesNotContain("site-falso");
    }

    @Test
    @DisplayName("Troca de senha logado manda alerta de segurança (só para e-mail confirmado)")
    void securityNotice() throws Exception {
        String email = email();
        String jwt = register(email);
        call(post("/api/auth/verify-email"), null, Map.of("token", tokenIn(lastTo(email))));
        reset(mailSender);
        assertThat(call(post("/api/account/password"), jwt, Map.of("currentPassword", PASSWORD, "newPassword", "Libelula5732x"))
                .getStatus()).isEqualTo(200);
        assertThat(lastTo(email).getSubject()).isEqualTo("Alerta de segurança: Sua senha foi alterada — ShopEasy");

        String unverified = email();
        String jwt2 = register(unverified);
        reset(mailSender);
        call(post("/api/account/password"), jwt2, Map.of("currentPassword", PASSWORD, "newPassword", "Libelula5732x"));
        assertThat(sent()).isEmpty();                                                   // endereço pode ser de outra pessoa
    }
}
