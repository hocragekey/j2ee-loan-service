package com.capitalone.loansvc.web;

import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SecurityHeadersFilterTest {

    @Test
    public void setsSecurityHeadersAndContinuesChain() throws Exception {
        Map<String, String> headers = new HashMap<>();
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName())) {
                        headers.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> null);
        boolean[] chained = {false};
        FilterChain chain = (request, response) -> chained[0] = true;

        new SecurityHeadersFilter().doFilter(req, resp, chain);

        assertTrue(chained[0]);
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("no-referrer", headers.get("Referrer-Policy"));
        String csp = headers.get("Content-Security-Policy");
        assertTrue(csp.contains("default-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("form-action 'self'"));
    }
}
