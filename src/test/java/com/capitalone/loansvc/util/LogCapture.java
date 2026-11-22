package com.capitalone.loansvc.util;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.capitalone.loansvc.db.DbUtil;

/**
 * Test helper that captures Log4j events and System.out so tests can assert on log content.
 */
public class LogCapture extends AppenderSkeleton {

    private final StringBuilder log4j = new StringBuilder();
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private PrintStream originalOut;

    public static LogCapture start() {
        LogCapture c = new LogCapture();
        Logger.getRootLogger().addAppender(c);
        c.originalOut = System.out;
        System.setOut(new PrintStream(c.stdout, true));
        return c;
    }

    public void stop() {
        Logger.getRootLogger().removeAppender(this);
        System.setOut(originalOut);
    }

    /** All Log4j messages plus everything printed to stdout. */
    public String all() {
        return log4j.toString() + "\n" + stdout.toString();
    }

    public String log4jOnly() {
        return log4j.toString();
    }

    public String stdoutOnly() {
        return stdout.toString();
    }

    public static String auditMessages(String applId) throws Exception {
        StringBuilder sb = new StringBuilder();
        Connection c = DbUtil.getConnection();
        try {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT EVT_TYPE, EVT_MSG FROM AUDIT_EVT WHERE APPL_ID = ? ORDER BY EVT_ID");
            ps.setString(1, applId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString(1)).append(' ').append(rs.getString(2)).append('\n');
            }
            rs.close();
            ps.close();
        } finally {
            c.close();
        }
        return sb.toString();
    }

    @Override
    protected synchronized void append(LoggingEvent event) {
        log4j.append(event.getRenderedMessage()).append('\n');
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
