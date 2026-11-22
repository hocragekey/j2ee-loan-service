package com.capitalone.loansvc.db;

import com.capitalone.loansvc.util.ConfigHolder;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbUtil {

    private static final Logger LOG = Logger.getLogger(DbUtil.class);
    private static boolean schemaLoaded = false;

    public static Connection getConnection() {
        try {
            Class.forName("org.h2.Driver");
            return DriverManager.getConnection(
                    ConfigHolder.get("db.url"),
                    ConfigHolder.get("db.user"),
                    ConfigHolder.get("db.pass"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("DB connection failed", e);
        }
    }

    public static synchronized void initSchema() {
        if (schemaLoaded) {
            return;
        }
        Connection c = null;
        try {
            c = getConnection();
            runScript(c, "/schema.sql");
            runScript(c, "/data.sql");
            schemaLoaded = true;
            LOG.info("H2 schema + seed data loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Schema init failed", e);
        } finally {
            closeQuietly(c);
        }
    }

    public static synchronized void reset() {
        Connection c = null;
        Statement st = null;
        try {
            c = getConnection();
            st = c.createStatement();
            st.execute("DROP ALL OBJECTS");
            schemaLoaded = false;
        } catch (Exception e) {
            System.out.println("DbUtil.reset error: " + e.getMessage());
        } finally {
            closeQuietly(st);
            closeQuietly(c);
        }
    }

    private static void runScript(Connection c, String resource) throws Exception {
        InputStream in = DbUtil.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + resource);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        Statement st = c.createStatement();
        try {
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("--")) {
                    continue;
                }
                sb.append(line).append(' ');
                if (line.endsWith(";")) {
                    String sql = sb.toString().trim();
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1);
                    }
                    st.execute(sql);
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0) {
                st.execute(sb.toString());
            }
        } finally {
            closeQuietly(st);
            br.close();
        }
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
