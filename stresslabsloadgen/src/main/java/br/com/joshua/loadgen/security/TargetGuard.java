package br.com.joshua.loadgen.security;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Garante que o gerador só dispare contra a API interna configurada.
 * O cliente informa apenas um caminho relativo, validado contra a allowlist de prefixos.
 */
public class TargetGuard {

    public static final Set<String> ALLOWED_HEADERS = Set.of("x-client-id", "accept", "x-request-tag");
    public static final String ID_PLACEHOLDER = "{id}";
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9/_\\-.?=&{}]{1,254}");
    private static final Pattern SAFE_HEADER_VALUE = Pattern.compile("[\\x20-\\x7E]{1,128}");

    private final URI baseUrl;
    private final List<String> allowedPrefixes;

    public TargetGuard(URI baseUrl, List<String> allowedPrefixes) {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("URL base do alvo inválida: " + baseUrl);
        }
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) {
            throw new IllegalArgumentException("Ao menos um prefixo de caminho permitido é obrigatório");
        }
        this.baseUrl = baseUrl;
        this.allowedPrefixes = List.copyOf(allowedPrefixes);
    }

    /** Valida o caminho e o converte em template de URI absoluto dentro da API interna. */
    public String validatePath(String path) {
        if (path == null || !SAFE_PATH.matcher(path).matches() || path.contains("..") || path.contains("//")) {
            throw new TargetNotAllowedException("Caminho inválido: " + path);
        }
        if (allowedPrefixes.stream().noneMatch(path::startsWith)) {
            throw new TargetNotAllowedException("Destino não autorizado: " + path);
        }
        String probe = path.replace(ID_PLACEHOLDER, "1");
        URI resolved = baseUrl.resolve(probe);
        if (!sameOrigin(resolved)) {
            throw new TargetNotAllowedException("Destino fora da API interna: " + path);
        }
        return path;
    }

    public URI resolve(String validatedPath, long id) {
        return baseUrl.resolve(validatedPath.replace(ID_PLACEHOLDER, Long.toString(id)));
    }

    public void validateHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((name, value) -> {
            if (name == null || !ALLOWED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw new TargetNotAllowedException("Header não permitido: " + name);
            }
            if (value == null || !SAFE_HEADER_VALUE.matcher(value).matches()) {
                throw new TargetNotAllowedException("Valor inválido para o header " + name);
            }
        });
    }

    private boolean sameOrigin(URI uri) {
        return baseUrl.getScheme().equals(uri.getScheme())
                && baseUrl.getHost().equalsIgnoreCase(uri.getHost())
                && baseUrl.getPort() == uri.getPort();
    }

    public static class TargetNotAllowedException extends RuntimeException {
        public TargetNotAllowedException(String message) {
            super(message);
        }
    }
}
