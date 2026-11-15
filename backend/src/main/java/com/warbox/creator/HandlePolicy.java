package com.warbox.creator;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Creator handle rules: normalization (trim + lowercase), format and reserved words. */
public final class HandlePolicy {

    public static final String REGEX = "^[a-z0-9][a-z0-9_-]{1,28}[a-z0-9]$";
    public static final Pattern PATTERN = Pattern.compile(REGEX);

    public static final Set<String> RESERVED = Set.of(
            "me", "admin", "administrador", "api", "app", "c", "criador", "criadores", "explorar",
            "entrar", "cadastro", "sair", "conta", "configuracoes", "ajuda", "suporte", "termos",
            "privacidade", "warbox", "assinar", "assinaturas", "videos", "null", "undefined");

    private HandlePolicy() {}

    public static String normalize(String handle) {
        return handle == null ? null : handle.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidFormat(String handle) {
        return handle != null && PATTERN.matcher(handle).matches();
    }

    public static boolean isReserved(String handle) {
        return handle != null && RESERVED.contains(handle);
    }
}
