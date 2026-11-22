-- PostgreSQL port of the legacy H2 seed (src/main/resources/data.sql): the
-- four rate-card rows and seed customers C10001-C10004, values unchanged.
-- MERGE INTO ... KEY(...) -> INSERT ... ON CONFLICT (...) DO UPDATE.
-- C10004 is the watchlist test record (KYC returns WL for it).

INSERT INTO rate_tbl (prod_cd, base_rate, min_fico, max_ltv) VALUES
    ('AUTO',     5.9900, 620, 100.0000),
    ('MORTGAGE', 6.7500, 640,  95.0000),
    ('HELOC',    7.2500, 660,  85.0000),
    ('PERSONAL', 9.9900, 660,   0.0000)
ON CONFLICT (prod_cd) DO UPDATE
    SET base_rate = EXCLUDED.base_rate,
        min_fico  = EXCLUDED.min_fico,
        max_ltv   = EXCLUDED.max_ltv;

INSERT INTO cust_acct (cust_id, first_nm, last_nm, ssn_last4, emp_nm, emp_status,
                       annual_income, monthly_debt, email_addr, phone_nbr, create_ts) VALUES
    ('C10001', 'Jane',   'Nguyen',  '4421', 'Acme Corp',       'EMPLOYED',       92000.00, 1450.00, 'jane.nguyen@example.com', '555-0101', CURRENT_TIMESTAMP),
    ('C10002', 'Marcus', 'Lee',     '8832', NULL,              'EMPLOYED',       68000.00, 2100.00, 'marcus.lee@example.com',  '555-0102', CURRENT_TIMESTAMP),
    ('C10003', 'Priya',  'Shah',    '1190', 'Shah Consulting', 'SELF_EMPLOYED', 110000.00, 1800.00, 'priya.shah@example.com',  '555-0103', CURRENT_TIMESTAMP),
    ('C10004', 'Watch',  'Listman', '0007', 'Unknown LLC',     'EMPLOYED',       50000.00,  900.00, 'watch.list@example.com',  '555-0199', CURRENT_TIMESTAMP)
ON CONFLICT (cust_id) DO UPDATE
    SET first_nm      = EXCLUDED.first_nm,
        last_nm       = EXCLUDED.last_nm,
        ssn_last4     = EXCLUDED.ssn_last4,
        emp_nm        = EXCLUDED.emp_nm,
        emp_status    = EXCLUDED.emp_status,
        annual_income = EXCLUDED.annual_income,
        monthly_debt  = EXCLUDED.monthly_debt,
        email_addr    = EXCLUDED.email_addr,
        phone_nbr     = EXCLUDED.phone_nbr,
        create_ts     = EXCLUDED.create_ts;
