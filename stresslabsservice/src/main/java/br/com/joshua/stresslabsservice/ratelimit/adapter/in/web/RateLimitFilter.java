package br.com.joshua.stresslabsservice.ratelimit.adapter.in.web;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService;
import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Aplica o Rate Limiting somente aos endpoints de teste (/api/target/**). */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";
    public static final String LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final Pattern SAFE_CLIENT_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ClientIdentificationStrategy strategy = rateLimitService.currentPolicy().clientStrategy();
        RateLimitDecision decision = rateLimitService.check(resolveClientKey(request, strategy));

        if (decision.limited()) {
            response.setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
            response.setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        }
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Limite de requisições excedido. Tente novamente em "
                + decision.retryAfterSeconds() + "s\"}");
    }

    static String resolveClientKey(HttpServletRequest request, ClientIdentificationStrategy strategy) {
        return switch (strategy) {
            case GLOBAL -> "global";
            case IP -> "ip:" + request.getRemoteAddr();
            case HEADER -> {
                String clientId = request.getHeader(CLIENT_ID_HEADER);
                yield clientId != null && SAFE_CLIENT_ID.matcher(clientId).matches()
                        ? "client:" + clientId
                        : "ip:" + request.getRemoteAddr();
            }
        };
    }
}
