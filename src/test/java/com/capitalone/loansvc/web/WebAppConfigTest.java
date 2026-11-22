package com.capitalone.loansvc.web;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WebAppConfigTest {

    private static final Path WEBAPP = Paths.get("src/main/webapp");
    private static final Pattern JSP_EXPRESSION = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    public void adminReloadEndpointIsNotMapped() throws Exception {
        String webXml = read(WEBAPP.resolve("WEB-INF/web.xml"));
        assertFalse(webXml.contains("/admin/reload"));
        assertFalse(webXml.contains("AdminReloadServlet"));
        assertFalse(read(WEBAPP.resolve("index.jsp")).contains("admin/reload"));
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
    public void securityHeadersFilterMappedToAllRequests() throws Exception {
        String webXml = read(WEBAPP.resolve("WEB-INF/web.xml"));
        assertTrue(webXml.contains("<filter-class>" + SecurityHeadersFilter.class.getName() + "</filter-class>"));
        assertTrue(webXml.contains("<url-pattern>/*</url-pattern>"));
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
