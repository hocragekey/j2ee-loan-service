package com.capitalone.loansvc.web;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Dependency-free servlet request/response fakes built on java.lang.reflect.Proxy.
 */
final class ServletTestSupport {

    private ServletTestSupport() {
    }

    static final class FakeRequest {
        final Map<String, String> params = new HashMap<String, String>();
        final Map<String, Object> attributes = new HashMap<String, Object>();
        String forwardedTo;

        FakeRequest param(String name, String value) {
            params.put(name, value);
            return this;
        }

        HttpServletRequest proxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method m, Object[] args) {
                            String n = m.getName();
                            if ("getParameter".equals(n)) {
                                return params.get(args[0]);
                            }
                            if ("setAttribute".equals(n)) {
                                attributes.put((String) args[0], args[1]);
                                return null;
                            }
                            if ("getAttribute".equals(n)) {
                                return attributes.get(args[0]);
                            }
                            if ("getRequestDispatcher".equals(n)) {
                                final String path = (String) args[0];
                                return Proxy.newProxyInstance(
                                        RequestDispatcher.class.getClassLoader(),
                                        new Class<?>[] {RequestDispatcher.class},
                                        new InvocationHandler() {
                                            @Override
                                            public Object invoke(Object p2, Method m2, Object[] a2) {
                                                forwardedTo = path;
                                                return null;
                                            }
                                        });
                            }
                            throw new UnsupportedOperationException(n);
                        }
                    });
        }
    }

    static final class FakeResponse {
        int status = HttpServletResponse.SC_OK;
        final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body, true);

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method m, Object[] args) {
                            String n = m.getName();
                            if ("setStatus".equals(n)) {
                                status = (Integer) args[0];
                                return null;
                            }
                            if ("getWriter".equals(n)) {
                                return writer;
                            }
                            throw new UnsupportedOperationException(n);
                        }
                    });
        }
    }
}
