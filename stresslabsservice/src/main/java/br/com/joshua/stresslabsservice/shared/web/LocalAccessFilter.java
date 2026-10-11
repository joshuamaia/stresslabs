package br.com.joshua.stresslabsservice.shared.web;

import java.io.IOException;
import java.net.InetAddress;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MVP: restringe operações administrativas e internas a clientes de rede local
 * (loopback, redes privadas e redes Docker). Não substitui autenticação/autorização.
 */
public class LocalAccessFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public LocalAccessFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || isLocal(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Acesso negado\",\"status\":403,"
                + "\"detail\":\"Operações administrativas disponíveis apenas no ambiente local\"}");
    }

    static boolean isLocal(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.ofLiteral(remoteAddress);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            // IPv6 Unique Local Address (fc00::/7)
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
