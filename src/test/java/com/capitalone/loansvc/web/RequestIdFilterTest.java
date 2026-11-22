package com.capitalone.loansvc.web;

import org.junit.Test;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RequestIdFilterTest {

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    public void safeInboundIdIsReused() {
        assertEquals("smoke-123", RequestIdFilter.resolve("smoke-123"));
        assertEquals("a.B_9-z", RequestIdFilter.resolve("a.B_9-z"));
    }

    @Test
    public void missingOrUnsafeInboundIdIsReplacedWithUuid() {
        assertTrue(RequestIdFilter.resolve(null).matches(UUID_RE));
        assertTrue(RequestIdFilter.resolve("").matches(UUID_RE));
        assertTrue(RequestIdFilter.resolve("bad id\nINJECTED").matches(UUID_RE));
        assertTrue(RequestIdFilter.resolve("x".repeat(65)).matches(UUID_RE));
        assertNotEquals(RequestIdFilter.resolve(null), RequestIdFilter.resolve(null));
    }

    @Test
    public void requestIdIsOnMdcDuringChainAndClearedAfter() throws Exception {
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getHeader": return RequestIdFilter.HEADER.equals(a[0]) ? "req-42" : null;
                        case "getAttribute": return null;
                        case "getDispatcherType": return jakarta.servlet.DispatcherType.REQUEST;
                        default: return null;
                    }
                });
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> null);
        AtomicReference<String> seen = new AtomicReference<String>();
        FilterChain chain = (rq, rs) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY));

        new RequestIdFilter().doFilter(req, resp, chain);

        assertEquals("req-42", seen.get());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
