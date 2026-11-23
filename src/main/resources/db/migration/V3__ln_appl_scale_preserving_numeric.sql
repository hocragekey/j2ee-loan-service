-- Read-back parity (TEST-03). PostgreSQL pads numeric(p,s) input to scale s, but H2 (legacy 1.4 and
-- 2.x) keeps the scale of the value written: 18000 stays 18000, an LTV of 0 stays 0. /underwrite
-- renders the read-back BigDecimal with toString(), so the padding changed the page (18000.00, 0.0000).
-- Unconstrained numeric keeps the written scale. The CHECKs keep the old integer-digit limits, and
-- LoanDao rounds HALF_UP beyond the old column scale, as H2 does.
-- numeric(p,s) -> numeric is binary-compatible (no table rewrite). Rows written before V3 keep
-- their padded scale.
ALTER TABLE ln_appl
    ALTER COLUMN req_amt    TYPE numeric,
    ALTER COLUMN collat_val TYPE numeric,
    ALTER COLUMN ltv_pct    TYPE numeric,
    ALTER COLUMN dti_pct    TYPE numeric,
    ALTER COLUMN base_rate  TYPE numeric,
    ALTER COLUMN final_rate TYPE numeric,
    ADD CONSTRAINT ln_appl_req_amt_range    CHECK (abs(req_amt)    < 1e12),
    ADD CONSTRAINT ln_appl_collat_val_range CHECK (abs(collat_val) < 1e12),
    ADD CONSTRAINT ln_appl_ltv_pct_range    CHECK (abs(ltv_pct)    < 1e4),
    ADD CONSTRAINT ln_appl_dti_pct_range    CHECK (abs(dti_pct)    < 1e4),
    ADD CONSTRAINT ln_appl_base_rate_range  CHECK (abs(base_rate)  < 1e4),
    ADD CONSTRAINT ln_appl_final_rate_range CHECK (abs(final_rate) < 1e4);
