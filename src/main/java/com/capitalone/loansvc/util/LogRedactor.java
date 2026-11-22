package com.capitalone.loansvc.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks PII before text reaches Log4j or AUDIT_EVT (SEC-03, OBS-04).
 */
public final class LogRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]*@([A-Za-z0-9.\\-]+)");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-?\\d{2}-?\\d{4}\\b");
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)\\b(fico|score|ssn|ssn_?last4|income|annual_?income|monthly_?debt|email|phone)"
                    + "(\\s*[=:]\\s*)[^\\s,;|]+");
    /** Faux bureau payload SCORE|FACTOR|FACTOR. */
    private static final Pattern BUREAU_PAYLOAD = Pattern.compile("\\b\\d{3}((\\|[A-Za-z_]+)+)");
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");

    private LogRedactor() {
    }

    /** jane.nguyen@example.com becomes j***@example.com. */
    public static String maskEmail(String email) {
        if (StringHelper.isEmpty(email)) {
            return email;
        }
        Matcher m = EMAIL.matcher(email.trim());
        if (m.matches()) {
            return m.group(1) + "***@" + m.group(2);
        }
        return REDACTED;
    }

    public static String redact(String msg) {
        if (msg == null) {
            return null;
        }
        String out = LINE_BREAKS.matcher(msg).replaceAll(" ");
        out = SENSITIVE_KEY_VALUE.matcher(out).replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        out = BUREAU_PAYLOAD.matcher(out).replaceAll(Matcher.quoteReplacement(REDACTED) + "$1");
        out = SSN.matcher(out).replaceAll(Matcher.quoteReplacement(REDACTED));
        Matcher m = EMAIL.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "***@" + m.group(2)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
