package com.capitalone.loansvc.web;

import com.capitalone.loansvc.LoanServiceApplication;
import com.capitalone.loansvc.manager.SeedCustomerGoldenTest;
import com.capitalone.loansvc.testsupport.PostgresSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TEST-03: GET /underwrite read-back of every golden case renders byte-for-byte like the legacy build
 * (underwrite-readback-legacy.html), on in-memory H2 and on PostgreSQL 16. Runs the real servlets/JSP
 * on an embedded Tomcat.
 */
@RunWith(Parameterized.class)
public class UnderwriteReadbackRenderingTest {

    private static final Pattern APPL_ID = Pattern.compile("LA\\d+");
    private final String database;

    public UnderwriteReadbackRenderingTest(String database) {
        this.database = database;
    }

    @Parameters(name = "{0}")
    public static List<Object[]> databases() {
        return Arrays.asList(new Object[][] {{"h2"}, {"postgres"}});
    }

    @Test
    public void readBackRendersLikeLegacyForAllGoldenCases() throws Exception {
        ConfigurableApplicationContext ctx;
        if ("postgres".equals(database)) {
            PostgresSupport.assumeDocker();
            PostgresSupport.reset(PostgresSupport.primary().getBean(DataSource.class));
            ctx = PostgresSupport.start(WebApplicationType.SERVLET, "--server.port=0",
                    "--spring.flyway.enabled=false", "--APP_REGION_ROLE=primary");
        } else {
            ctx = new SpringApplicationBuilder(LoanServiceApplication.class).web(WebApplicationType.SERVLET)
                    .run("--server.port=0", "--DB_HOST=",
                            "--spring.datasource.url=jdbc:h2:mem:loansvc-readback;DB_CLOSE_DELAY=-1;MODE=Oracle");
        }
        try {
            int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
            String base = "http://localhost:" + port + "/loan-service";
            Map<String, String> legacy = legacyPages();
            HttpClient http = HttpClient.newHttpClient();
            List<String> mismatches = new ArrayList<String>();
            for (Object[] row : SeedCustomerGoldenTest.rows()) {
                @SuppressWarnings("unchecked")
                Map<String, String> r = (Map<String, String>) row[1];
                String name = r.get("customerId") + "-" + r.get("productCode");
                Thread.sleep(2); // application IDs are LA<currentTimeMillis>
                String applyBody = http.send(HttpRequest.newBuilder(URI.create(base + "/apply"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form(r))).build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                Matcher m = APPL_ID.matcher(applyBody);
                assertTrue(name + ": no application id in /apply response", m.find());
                String id = m.group();
                String page = http.send(HttpRequest.newBuilder(URI.create(base + "/underwrite?applId=" + id)).build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                String actual = normalize(page.replace(id, "LA<id>"));
                String expected = legacy.get(name);
                assertNotNull("legacy capture for " + name, expected);
                if (!expected.equals(actual)) {
                    mismatches.add(name + firstDifference(expected, actual));
                }
            }
            assertEquals(database + " read-back differs from legacy: " + mismatches, 0, mismatches.size());
        } finally {
            ctx.close();
        }
    }

    private static String form(Map<String, String> r) {
        String[][] fields = {{"customerId", "customerId"}, {"productCode", "productCode"}, {"amount", "requestedAmount"},
                {"termMonths", "termMonths"}, {"collateralType", "collateralType"},
                {"collateralValue", "collateralValue"}, {"purposeCode", "purposeCode"}};
        StringBuilder sb = new StringBuilder();
        for (String[] f : fields) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            String v = r.get(f[1]);
            sb.append(f[0]).append('=').append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> legacyPages() throws Exception {
        InputStream in = UnderwriteReadbackRenderingTest.class.getResourceAsStream("/golden/underwrite-readback-legacy.html");
        assertNotNull(in);
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> pages = new LinkedHashMap<String, String>();
        String name = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("=== ")) {
                if (name != null) {
                    pages.put(name, normalize(body.toString()));
                }
                name = line.substring(4).trim();
                body.setLength(0);
            } else if (name != null) {
                body.append(line).append('\n');
            }
        }
        if (name != null) {
            pages.put(name, normalize(body.toString()));
        }
        assertEquals("legacy pages", 16, pages.size());
        return pages;
    }

    private static String normalize(String html) {
        return html.replace("\r", "").replaceAll("\\s+$", "");
    }

    private static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n");
        String[] a = actual.split("\n");
        for (int i = 0; i < Math.max(e.length, a.length); i++) {
            String el = i < e.length ? e[i] : "<eof>";
            String al = i < a.length ? a[i] : "<eof>";
            if (!el.equals(al)) {
                return " [line " + (i + 1) + ": expected '" + el.trim() + "' but was '" + al.trim() + "']";
            }
        }
        return "";
    }
}
