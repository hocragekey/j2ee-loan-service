package com.capitalone.loansvc.util;

import com.capitalone.loansvc.testsupport.TestBeans;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** OBS-01: console logs are structured JSON (ECS) and log4j 1.x is gone. */
public class StructuredLoggingTest {

    @Test
    public void consoleFormatIsEcsJson() {
        assertEquals("ecs", TestBeans.context().getEnvironment().getProperty("logging.structured.format.console"));
    }

    @Test(expected = ClassNotFoundException.class)
    public void log4jOneIsNotOnTheClasspath() throws Exception {
        Class.forName("org.apache.log4j.Logger");
    }
}
