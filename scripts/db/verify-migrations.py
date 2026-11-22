#!/usr/bin/env python3
"""Apply the Flyway migrations to a throwaway PostgreSQL 16 (docker compose) and
prove they are a faithful port of the legacy H2 schema.sql / data.sql.

Expectations are parsed from src/main/resources/schema.sql and data.sql, so any
drift between the legacy files and the migrations fails the check.

    python3 scripts/db/verify-migrations.py          # needs Docker with compose v2
    python3 scripts/db/verify-migrations.py --keep   # leave the container running
"""
import os
import re
import subprocess
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
PROJECT = "loansvc-migration-test"
SCHEMA = "loan_svc"
NULL = "<NULL>"
ENV = {**os.environ, "LOCAL_DB_PORT": ""}  # random host port; never clashes with a dev DB
failures = []
sys.stdout.reconfigure(line_buffering=True)


def compose(*args, capture=False):
    cmd = ["docker", "compose", "-p", PROJECT, "-f", str(ROOT / "docker-compose.yml"), *args]
    res = subprocess.run(cmd, env=ENV, text=True, capture_output=capture)
    if res.returncode != 0:
        sys.exit(f"FAILED: {' '.join(cmd)}\n{res.stdout or ''}{res.stderr or ''}")
    return res.stdout if capture else None


def sql(query):
    out = compose("exec", "-T", "postgres", "psql", "-U", "loansvc", "-d", "loanservice",
                  "-v", "ON_ERROR_STOP=1", "-X", "-q", "-At", "-F", "\t", "-P", f"null={NULL}",
                  "-c", f"SET search_path TO {SCHEMA}; {query}", capture=True)
    return [line.split("\t") for line in out.splitlines() if line]


def check(cond, msg):
    print(("  ok   " if cond else "  FAIL ") + msg)
    if not cond:
        failures.append(msg)


H2_TYPES = [
    (r"VARCHAR\((\d+)\)", lambda m: f"character varying({m[1]})"),
    (r"CHAR\((\d+)\)", lambda m: f"character({m[1]})"),
    (r"DECIMAL\((\d+),(\d+)\)", lambda m: f"numeric({m[1]},{m[2]})"),
    (r"INT", lambda m: "integer"),
    (r"BIGINT", lambda m: "bigint"),
    (r"TIMESTAMP", lambda m: "timestamp without time zone"),
]


def pg_type(h2):
    for pattern, conv in H2_TYPES:
        m = re.fullmatch(pattern, h2, re.I)
        if m:
            return conv(m)
    raise ValueError(f"unmapped H2 type {h2}")


def legacy_schema():
    tables = {}
    text = (RES / "schema.sql").read_text()
    for name, body in re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\);", text, re.S):
        cols = []
        for line in body.strip().splitlines():
            parts = line.strip().rstrip(",").split()
            rest = " ".join(parts[2:]).upper()
            cols.append({"name": parts[0].lower(), "type": pg_type(parts[1]),
                         "pk": "PRIMARY KEY" in rest, "identity": "AUTO_INCREMENT" in rest or "AS IDENTITY" in rest})
        tables[name.lower()] = cols
    return tables


def split_values(s):
    vals, cur, quoted = [], "", False
    for ch in s:
        if ch == "'":
            quoted = not quoted
        if ch == "," and not quoted:
            vals.append(cur.strip())
            cur = ""
        else:
            cur += ch
    vals.append(cur.strip())
    return vals


def legacy_seed():
    rows = {}
    text = (RES / "data.sql").read_text()
    pattern = r"MERGE INTO (\w+)\s*\(([^)]*)\)\s*KEY\((\w+)\)\s*VALUES\s*\((.*?)\);"
    for table, cols, key, vals in re.findall(pattern, text, re.S):
        cols = [c.strip().lower() for c in cols.split(",")]
        rows.setdefault(table.lower(), (key.lower(), []))[1].append(dict(zip(cols, split_values(vals))))
    return rows


def same_value(expected, actual, col_type):
    if expected.upper() == "NULL":
        return actual == NULL
    if expected.upper() == "CURRENT_TIMESTAMP":
        return actual != NULL
    if expected.startswith("'"):
        return actual == expected[1:-1].replace("''", "'")
    if col_type.startswith("numeric"):
        scale = int(col_type.rstrip(")").split(",")[1])
        # Same value at the column scale, rendered the way JDBC getBigDecimal returns it.
        return actual == f"{Decimal(expected):.{scale}f}"
    return Decimal(actual) == Decimal(expected)


def main():
    keep = "--keep" in sys.argv
    compose("down", "-v", "--remove-orphans", capture=True)
    try:
        print("==> start PostgreSQL 16 and run Flyway migrate")
        compose("up", "-d", "--wait", "postgres")
        compose("run", "--rm", "flyway", "migrate")
        schema = legacy_schema()
        seed = legacy_seed()

        print("==> Flyway history")
        hist = sql("SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
        check(hist == [["1", "t"], ["2", "t"]], f"V1 and V2 applied successfully ({hist})")

        print("==> tables and columns match schema.sql")
        actual_tables = {r[0] for r in sql(
            "SELECT table_name FROM information_schema.tables "
            f"WHERE table_schema = '{SCHEMA}' AND table_name <> 'flyway_schema_history'")}
        check(actual_tables == set(schema), f"tables {sorted(actual_tables)}")
        for table, cols in schema.items():
            actual = sql(
                "SELECT a.attname, format_type(a.atttypid, a.atttypmod), a.attidentity <> '' "
                "FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid "
                "JOIN pg_namespace n ON n.oid = c.relnamespace "
                f"WHERE n.nspname = '{SCHEMA}' AND c.relname = '{table}' AND a.attnum > 0 "
                "AND NOT a.attisdropped ORDER BY a.attnum")
            expected = [[c["name"], c["type"], "t" if c["identity"] else "f"] for c in cols]
            check(actual == expected, f"{table}: {len(cols)} columns, names/types/order/identity")
            pk = [r[0] for r in sql(
                "SELECT a.attname FROM pg_index i JOIN pg_attribute a "
                "ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                f"WHERE i.indrelid = '{SCHEMA}.{table}'::regclass AND i.indisprimary")]
            check(pk == [c["name"] for c in cols if c["pk"]], f"{table}: primary key {pk}")

        print("==> seed rows match data.sql")
        for table, (key, rows) in seed.items():
            types = {c["name"]: c["type"] for c in schema[table]}
            count = sql(f"SELECT count(*) FROM {table}")[0][0]
            check(int(count) == len(rows), f"{table}: {count} rows (expected {len(rows)})")
            for row in rows:
                cols = list(row)
                got = sql(f"SELECT {', '.join(cols)} FROM {table} WHERE {key} = {row[key]}")
                ok = len(got) == 1 and all(same_value(row[c], v, types[c]) for c, v in zip(cols, got[0]))
                check(ok, f"{table} {row[key]}: {got[0] if got else 'missing'}")

        print("==> legacy DAO statements run unchanged (rolled back)")
        rate = sql("SELECT BASE_RATE FROM RATE_TBL WHERE PROD_CD = 'HELOC'")
        check(rate == [["7.2500"]], f"LoanDao.lookupBaseRate HELOC -> {rate}")
        cust = sql("SELECT CUST_ID, EMP_NM, EMP_STATUS, ANNUAL_INCOME, MONTHLY_DEBT FROM CUST_ACCT WHERE CUST_ID = 'C10002'")
        check(cust == [["C10002", NULL, "EMPLOYED", "68000.00", "2100.00"]], f"CustomerDao.findById C10002 -> {cust}")
        dml = sql(
            "BEGIN; "
            "INSERT INTO LN_APPL (APPL_ID, CUST_ID, PROD_CD, REQ_AMT, TERM_MOS, STATUS_CD, CREATE_TS, UPDATE_TS) "
            "VALUES ('APP-TEST-1', 'C10001', 'AUTO', 25000.00, 60, 'APPROVED', now(), now()); "
            "INSERT INTO AUDIT_EVT (APPL_ID, EVT_TYPE, EVT_MSG, EVT_TS) VALUES ('APP-TEST-1', 'CREATED', 'x', now()); "
            "INSERT INTO DOC_CHKLIST (APPL_ID, DOC_TYPE, REQ_FLG, RCVD_FLG) VALUES ('APP-TEST-1', 'ID', 'Y', 'N'); "
            "SELECT (SELECT count(*) FROM AUDIT_EVT WHERE EVT_ID IS NOT NULL), "
            "(SELECT count(*) FROM DOC_CHKLIST WHERE DOC_ID IS NOT NULL), "
            "(SELECT REQ_AMT FROM LN_APPL WHERE APPL_ID = 'APP-TEST-1'); ROLLBACK;")
        check(dml == [["1", "1", "25000.00"]], f"LoanDao/AuditDao/DocumentManager inserts, ids generated -> {dml}")

        print("==> re-running migrate is a no-op")
        compose("run", "--rm", "flyway", "migrate", capture=True)
        again = sql("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL")
        check(again == [["2"]], "no new migrations applied")
    finally:
        if not keep:
            compose("down", "-v", "--remove-orphans", capture=True)

    if failures:
        sys.exit(f"\n{len(failures)} check(s) failed")
    print("\nOK: migrations are a faithful PostgreSQL port of schema.sql/data.sql")


if __name__ == "__main__":
    main()
