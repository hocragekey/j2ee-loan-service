package com.capitalone.loansvc.manager;

import com.capitalone.loansvc.testsupport.PostgresSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** TEST-03 / PLAT-04: the 16-case golden matrix must give identical results on PostgreSQL. */
@RunWith(Parameterized.class)
public class PostgresParityTest {

    private final Map<String, String> row;

    public PostgresParityTest(String name, Map<String, String> row) {
        this.row = row;
    }

    @Parameters(name = "postgres-{0}")
    public static List<Object[]> rows() throws Exception {
        return SeedCustomerGoldenTest.rows();
    }

    @BeforeClass
    public static void requireDocker() {
        PostgresSupport.assumeDocker();
    }

    @Before
    public void setUp() {
        PostgresSupport.reset(PostgresSupport.primary().getBean(DataSource.class));
    }

    @Test
    public void processApplicationMatchesGoldenOnPostgres() {
        SeedCustomerGoldenTest.assertMatchesGolden(row,
                PostgresSupport.primary().getBean(LoanApplicationManager.class));
    }
}
