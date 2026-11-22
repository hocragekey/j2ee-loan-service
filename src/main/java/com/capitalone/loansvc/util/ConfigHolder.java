package com.capitalone.loansvc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Global mutable config bag. Intentionally not thread-safe / not typed.
 */
public class ConfigHolder {

    private static final Map<String, String> PROPS = new HashMap<String, String>();

    static {
        PROPS.put("db.url", "jdbc:h2:mem:loansvc;DB_CLOSE_DELAY=-1;MODE=Oracle");
        PROPS.put("db.user", "sa");
        PROPS.put("db.pass", "");
        PROPS.put("bureau.mock", "Y");
        PROPS.put("notify.enabled", "Y");
        PROPS.put("dti.default", "43");
        PROPS.put("dti.heloc", "45");
        PROPS.put("fico.floor", "620");
    }

    public static String get(String key) {
        return PROPS.get(key);
    }

    public static String get(String key, String def) {
        String v = PROPS.get(key);
        if (v == null) {
            return def;
        }
        return v;
    }

    public static void put(String key, String val) {
        PROPS.put(key, val);
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(PROPS.get(key));
        } catch (Exception e) {
            return def;
        }
    }
}
