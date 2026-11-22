package com.capitalone.loansvc.config;

import com.capitalone.loansvc.testsupport.TestBeans;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LoanPropertiesTest {

    @Test
    public void defaultsMatchLegacyConfigHolder() {
        LoanProperties p = new LoanProperties();
        assertEquals("Y", p.getBureauMock());
        assertEquals("Y", p.getNotifyEnabled());
        assertEquals("43", p.getDtiDefault());
        assertEquals("45", p.getDtiHeloc());
        assertEquals(620, p.ficoFloorOrDefault());
        assertEquals("local", p.getRegionId());
    }

    @Test
    public void applicationPropertiesBindToLegacyValues() {
        LoanProperties p = TestBeans.get(LoanProperties.class);
        assertEquals("Y", p.getBureauMock());
        assertEquals("43", p.getDtiDefault());
        assertEquals("620", p.getFicoFloor());
    }

    @Test
    public void environmentVariablesOverrideProperties() {
        Map<String, Object> env = new HashMap<String, Object>();
        env.put("LOAN_BUREAU_MOCK", "N");
        env.put("LOAN_FICO_FLOOR", "640");
        env.put("LOAN_REGION_ID", "us-east-1");
        Map<String, Object> file = new HashMap<String, Object>();
        file.put("loan.bureau-mock", "Y");
        file.put("loan.dti-default", "43");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application.properties", file));
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env", env));

        LoanProperties p = new Binder(ConfigurationPropertySources.get(environment))
                .bind("loan", LoanProperties.class).get();

        assertEquals("N", p.getBureauMock());
        assertEquals(640, p.ficoFloorOrDefault());
        assertEquals("us-east-1", p.getRegionId());
        assertEquals("43", p.getDtiDefault());
    }

    @Test
    public void unparseableFicoFloorFallsBackTo620LikeConfigHolderGetInt() {
        LoanProperties p = new LoanProperties();
        p.setFicoFloor("abc");
        assertEquals(620, p.ficoFloorOrDefault());
        p.setFicoFloor(null);
        assertEquals(620, p.ficoFloorOrDefault());
    }
}
