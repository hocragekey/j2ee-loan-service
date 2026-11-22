package com.capitalone.loansvc.web;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HtmlEscaperTest {

    @Test
    public void escapesScriptInElementContent() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                HtmlEscaper.escape("<script>alert(1)</script>"));
    }

    @Test
    public void escapesAttributeBreakout() {
        String escaped = HtmlEscaper.escape("\"><svg onload=alert('x')>&");
        assertEquals("&quot;&gt;&lt;svg onload=alert(&#39;x&#39;)&gt;&amp;", escaped);
        assertFalse(escaped.contains("\""));
        assertFalse(escaped.contains("<"));
    }

    @Test
    public void leavesOrdinaryValuesUnchanged() {
        assertEquals("C10001", HtmlEscaper.escape("C10001"));
        assertEquals("25000.00", HtmlEscaper.escape(new BigDecimal("25000.00")));
        assertEquals("null", HtmlEscaper.escape(null));
    }
}
