package com.capitalone.loansvc.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds baseline browser security headers to every response.
 * The JSPs use inline style blocks only (no scripts, frames or external assets).
 */
public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self'; "
                    + "form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("X-Frame-Options", "DENY");
            resp.setHeader("Referrer-Policy", "no-referrer");
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
