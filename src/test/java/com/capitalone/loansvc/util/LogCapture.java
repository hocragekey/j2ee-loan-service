package com.capitalone.loansvc.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import com.capitalone.loansvc.testsupport.LegacyTestState;
import com.capitalone.loansvc.testsupport.TestBeans;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Test helper that captures application log events (SLF4J/Logback) and raw System.out so tests
 * can assert on log content. The console appender is detached while capturing so stdout only
 * holds writes that bypass the logging framework.
 */
public class LogCapture {

    private final StringBuilder events = new StringBuilder();
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final List<Appender<ILoggingEvent>> detached = new ArrayList<Appender<ILoggingEvent>>();
    private PrintStream originalOut;
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
        @Override
        protected void append(ILoggingEvent event) {
            synchronized (events) {
                events.append(event.getFormattedMessage()).append('\n');
            }
        }
    };

    public static LogCapture start() {
        TestBeans.context();
        LogCapture c = new LogCapture();
        Logger root = root();
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            c.detached.add(it.next());
        }
        for (Appender<ILoggingEvent> a : c.detached) {
            root.detachAppender(a);
        }
        c.appender.setContext(root.getLoggerContext());
        c.appender.start();
        root.addAppender(c.appender);
        c.originalOut = System.out;
        System.setOut(new PrintStream(c.stdout, true));
        return c;
    }

    public void stop() {
        Logger root = root();
        root.detachAppender(appender);
        for (Appender<ILoggingEvent> a : detached) {
            root.addAppender(a);
        }
        System.setOut(originalOut);
        appender.stop();
    }

    /** All application log messages plus everything printed to stdout. */
    public String all() {
        return events.toString() + "\n" + stdout.toString();
    }

    public String loggedOnly() {
        return events.toString();
    }

    public String stdoutOnly() {
        return stdout.toString();
    }

    public static String auditMessages(String applId) throws Exception {
        StringBuilder sb = new StringBuilder();
        Connection c = LegacyTestState.connection();
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

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
