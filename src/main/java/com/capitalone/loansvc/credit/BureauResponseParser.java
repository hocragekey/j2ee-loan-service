package com.capitalone.loansvc.credit;

/**
 * Parses faux bureau pipe-delimited payloads: SCORE|FACTOR1|FACTOR2
 */
public class BureauResponseParser {

    public int parseScore(String raw) {
        if (raw == null || raw.length() == 0) {
            return 0;
        }
        try {
            String[] parts = raw.split("\\|");
            return Integer.parseInt(parts[0].trim());
        } catch (Exception e) {
            System.out.println("BureauResponseParser bad payload: " + raw);
            return 0;
        }
    }

    public String parsePrimaryFactor(String raw) {
        if (raw == null) {
            return "UNK";
        }
        String[] parts = raw.split("\\|");
        if (parts.length > 1) {
            return parts[1];
        }
        return "UNK";
    }
}
