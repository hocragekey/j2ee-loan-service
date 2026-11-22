package com.capitalone.loansvc.web;

import com.capitalone.loansvc.testsupport.TestBeans;
import org.junit.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WebAppConfigTest {

    private static final Path WEBAPP = Paths.get("src/main/webapp");
    private static final Pattern JSP_EXPRESSION = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Collection<ServletRegistrationBean> servlets() {
        return TestBeans.context().getBeansOfType(ServletRegistrationBean.class).values();
    }

    @Test
    public void adminReloadEndpointIsNotMapped() throws Exception {
        for (ServletRegistrationBean<?> reg : servlets()) {
            assertFalse(reg.getUrlMappings().contains("/admin/reload"));
            assertFalse(reg.getServletName().contains("AdminReloadServlet"));
        }
        assertFalse(read(WEBAPP.resolve("index.jsp")).contains("admin/reload"));
    }

    @Test
    public void legacyServletEndpointsAreMapped() {
        Set<String> mappings = new HashSet<String>();
        for (ServletRegistrationBean<?> reg : servlets()) {
            mappings.addAll(reg.getUrlMappings());
        }
        assertEquals(new HashSet<String>(Arrays.asList("/apply", "/eligibility", "/underwrite", "/customer")),
                mappings);
    }

    @Test
    public void adminReloadServletClassIsGone() {
        try {
            Class.forName("com.capitalone.loansvc.web.AdminReloadServlet");
            fail("AdminReloadServlet must not exist");
        } catch (ClassNotFoundException expected) {
            // removed
        }
    }

    @Test
    public void securityHeadersFilterMappedToAllRequests() {
        boolean found = false;
        for (FilterRegistrationBean<?> reg : TestBeans.context().getBeansOfType(FilterRegistrationBean.class).values()) {
            if (reg.getFilter() instanceof SecurityHeadersFilter) {
                found = true;
                assertEquals(Collections.singleton("/*"), new HashSet<String>(reg.getUrlPatterns()));
            }
        }
        assertTrue(found);
    }

    @Test
    public void everyJspExpressionIsHtmlEscaped() throws Exception {
        int checked = 0;
        try (DirectoryStream<Path> jsps = Files.newDirectoryStream(WEBAPP, "*.jsp")) {
            for (Path jsp : jsps) {
                Matcher m = JSP_EXPRESSION.matcher(read(jsp));
                while (m.find()) {
                    checked++;
                    assertTrue("Unescaped JSP expression in " + jsp.getFileName() + ": " + m.group(),
                            m.group(1).contains("HtmlEscaper.escape("));
                }
            }
        }
        assertTrue(checked > 0);
    }
}
