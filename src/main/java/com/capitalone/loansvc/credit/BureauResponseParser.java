package com.capitalone.loansvc.credit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses faux bureau pipe-delimited payloads: SCORE|FACTOR1|FACTOR2
 */
public class BureauResponseParser {

    private static final Logger LOG = LoggerFactory.getLogger(BureauResponseParser.class);

    public int parseScore(String raw) {
        if (raw == null || raw.length() == 0) {
            return 0;
        }
        try {
            String[] parts = raw.split("\\|");
            return Integer.parseInt(parts[0].trim());
        } catch (Exception e) {
            LOG.warn("BureauResponseParser bad payload: " + raw);
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
