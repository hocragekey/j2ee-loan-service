package com.capitalone.loansvc.testsupport;

import com.capitalone.loansvc.util.AppContext;

/**
 * Single place that resets the legacy static state (AppContext singletons + shared in-memory H2)
 * so tests do not depend on execution order.
 */
public final class LegacyTestState {

    private LegacyTestState() {
    }

    /** Drops all H2 objects, reloads schema + seed data and rebuilds AppContext singletons. */
    public static void resetDatabase() {
        AppContext.reload();
    }
}
