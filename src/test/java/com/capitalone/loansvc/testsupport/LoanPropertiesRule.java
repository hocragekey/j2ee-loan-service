package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.config.LoanProperties;
import org.junit.rules.ExternalResource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Overrides {@link LoanProperties} values (addressed by their legacy ConfigHolder keys) for one
 * test and restores the originals afterwards.
 */
public class LoanPropertiesRule extends ExternalResource {

    private final Map<String, String> originals = new LinkedHashMap<String, String>();

    public void set(String key, String value) {
        LoanProperties props = TestBeans.get(LoanProperties.class);
        if (!originals.containsKey(key)) {
            originals.put(key, get(props, key));
        }
        put(props, key, value);
    }

    @Override
    protected void after() {
        LoanProperties props = TestBeans.get(LoanProperties.class);
        for (Map.Entry<String, String> e : originals.entrySet()) {
            put(props, e.getKey(), e.getValue());
        }
        originals.clear();
    }

    private static String get(LoanProperties p, String key) {
        switch (key) {
            case "bureau.mock": return p.getBureauMock();
            case "notify.enabled": return p.getNotifyEnabled();
            case "dti.default": return p.getDtiDefault();
            case "dti.heloc": return p.getDtiHeloc();
            case "fico.floor": return p.getFicoFloor();
            default: throw new IllegalArgumentException("unknown legacy key " + key);
        }
    }

    private static void put(LoanProperties p, String key, String value) {
        switch (key) {
            case "bureau.mock": p.setBureauMock(value); break;
            case "notify.enabled": p.setNotifyEnabled(value); break;
            case "dti.default": p.setDtiDefault(value); break;
            case "dti.heloc": p.setDtiHeloc(value); break;
            case "fico.floor": p.setFicoFloor(value); break;
            default: throw new IllegalArgumentException("unknown legacy key " + key);
        }
    }
}
