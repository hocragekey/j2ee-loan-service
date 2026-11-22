package com.capitalone.loansvc.db;

/** JDBC resource helpers shared by the DataSource-backed DAOs. */
public final class Jdbc {

    private Jdbc() {
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }
}
