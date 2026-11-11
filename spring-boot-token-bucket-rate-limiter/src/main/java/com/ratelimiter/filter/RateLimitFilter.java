package com.ratelimiter.filter;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.ratelimiter.config.RateLimitProperties;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.exception.RateLimitExceededException;
import com.ratelimiter.model.RateLimitDecision;
import com.ratelimiter.service.TokenBucketRateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet-filter equivalent of {@code RedisRateLimiterMiddleware}.
 * Clients are keyed by remote address. Exceeding the bucket raises
 * {@link RateLimitExceededException}, which {@code GlobalExceptionHandler} turns into HTTP 429.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String DECISION_ATTRIBUTE = "rateLimitDecision";

    private final TokenBucketRateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectProvider<HandlerExceptionResolver> exceptionResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            TokenBucketRateLimitService rateLimitService,
            RateLimitProperties properties,
            @Qualifier("handlerExceptionResolver") ObjectProvider<HandlerExceptionResolver> exceptionResolver) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return !matches(properties.getIncludePathPatterns(), path)
                || matches(properties.getExcludePathPatterns(), path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, java.io.IOException {
        String clientKey = clientKey(request);
        RateLimitDecision decision = rateLimitService.consume(clientKey);
        request.setAttribute(DECISION_ATTRIBUTE, decision);

        if (!decision.allowed()) {
            ModelAndView resolved = exceptionResolver.getObject().resolveException(
                    request,
                    response,
                    null,
                    new RateLimitExceededException(decision, requestPath(request)));
            if (resolved == null && !response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            }
            return;
        }

        response.setHeader(GlobalExceptionHandler.LIMIT_HEADER, Long.toString(decision.limit()));
        response.setHeader(GlobalExceptionHandler.REMAINING_HEADER, Long.toString(decision.remaining()));
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr;
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean matches(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
