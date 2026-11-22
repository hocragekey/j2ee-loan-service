package com.capitalone.loansvc.web;

/**
 * Escapes values for safe output in HTML element content and quoted attributes.
 */
public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    public static String escape(Object value) {
        String s = String.valueOf(value);
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}
