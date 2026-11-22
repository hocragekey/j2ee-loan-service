package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.util.ConfigHolder;
import org.junit.rules.ExternalResource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Overrides global {@link ConfigHolder} keys for one test and restores the originals afterwards.
 */
public class ConfigHolderRule extends ExternalResource {

    private final Map<String, String> originals = new LinkedHashMap<String, String>();

    public void set(String key, String value) {
        if (!originals.containsKey(key)) {
            originals.put(key, ConfigHolder.get(key));
        }
        ConfigHolder.put(key, value);
    }

    @Override
    protected void after() {
        for (Map.Entry<String, String> e : originals.entrySet()) {
            ConfigHolder.put(e.getKey(), e.getValue());
        }
        originals.clear();
    }
}
