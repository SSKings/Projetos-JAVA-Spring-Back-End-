package com.sskings.ecommerce.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Senha fácil de adivinhar, além da política de formato (8+ caracteres, letras e números). Revisão 5, R5-1: o teto
 * de tentativas por conta ainda permite alguns milhares de chutes por mês, e "senha123", "password1" ou "admin123"
 * estão no começo de qualquer lista de ataque. Recusa:
 * <ul>
 *   <li>senhas da lista {@code security/common-passwords.txt};</li>
 *   <li>palavra fraca com números/símbolos em volta ("Senha2026!", "brasil123", "123mudar");</li>
 *   <li>quase só números ("12345678a": até 2 letras);</li>
 *   <li>o nome da loja, a parte local do e-mail ou um nome da pessoa dentro da senha.</li>
 * </ul>
 * Comparação sem maiúsculas e sem acentos. Mensagens em inglês, como as demais da API (o front traduz).
 */
public final class PasswordStrength {

    public static final String TOO_COMMON = "Password is too common. Choose a less predictable one";
    public static final String PERSONAL = "Password must not contain your name, your email or the store name";

    static final Set<String> WEAK_WORDS = Set.of(
            "senha", "password", "passw", "admin", "administrador", "mudar", "trocar", "brasil", "teste", "test",
            "qwerty", "asdf", "abc", "abcd", "abcde", "loja", "shop", "shopeasy", "bemvindo", "welcome", "login",
            "acesso", "usuario", "user", "iloveyou", "teamo", "amor", "deus", "jesus", "familia", "flamengo",
            "corinthians", "palmeiras", "santos", "saopaulo", "gremio", "vasco", "cruzeiro", "futebol", "football",
            "princesa", "dragon", "monkey", "master", "letmein", "batman", "superman");

    private static volatile Set<String> common;

    private PasswordStrength() {}

    /** Problema da senha, ou vazio se ela é aceitável. name/email podem ser nulos. */
    public static Optional<String> problem(String password, String name, String email) {
        if (password == null) return Optional.empty();
        String pw = fold(password);
        if (commonPasswords().contains(pw)) return Optional.of(TOO_COMMON);
        String core = pw.replaceAll("^[^a-z]+|[^a-z]+$", "");
        // "S3nh@2026": tirar só dígitos e pontuação das pontas preserva o "@" do meio da palavra disfarçada
        String leetCore = unleet(pw.replaceAll("^[\\d\\p{Punct}&&[^@$!]]+|[\\d\\p{Punct}&&[^@$!]]+$", ""));
        if (WEAK_WORDS.contains(core) || WEAK_WORDS.contains(unleet(core)) || WEAK_WORDS.contains(leetCore)) {
            return Optional.of(TOO_COMMON);
        }
        if (pw.chars().filter(Character::isLetter).count() <= 2) return Optional.of(TOO_COMMON);
        if (pw.contains("shopeasy")) return Optional.of(PERSONAL);
        if (email != null) {
            String local = fold(email.split("@", 2)[0]).replaceAll("[^a-z0-9]", "");
            if (local.length() >= 4 && pw.replaceAll("[^a-z0-9]", "").contains(local)) return Optional.of(PERSONAL);
        }
        if (name != null) {
            for (String part : fold(name).split("[^a-z]+")) {
                if (part.length() >= 4 && pw.contains(part)) return Optional.of(PERSONAL);
            }
        }
        return Optional.empty();
    }

    /** "p4ssw0rd" → "password", "s3nh@" → "senha": troca de letra por número/símbolo não disfarça a palavra. */
    static String unleet(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(switch (c) {
                case '0' -> 'o'; case '1', '!' -> 'i'; case '3' -> 'e'; case '4', '@' -> 'a';
                case '5', '$' -> 's'; case '7' -> 't'; default -> c;
            });
        }
        return sb.toString();
    }

    static String fold(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT);
    }

    static Set<String> commonPasswords() {
        Set<String> c = common;
        if (c == null) {
            try (var in = PasswordStrength.class.getResourceAsStream("/security/common-passwords.txt")) {
                if (in == null) throw new IllegalStateException("security/common-passwords.txt not found");
                c = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                        .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .map(PasswordStrength::fold).collect(Collectors.toUnmodifiableSet());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read security/common-passwords.txt", e);
            }
            common = c;
        }
        return c;
    }
}
