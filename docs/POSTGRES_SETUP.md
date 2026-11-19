# NETRA Local PostgreSQL Setup Guide (Windows)

This document provides instructions for initializing and verifying the authoritative PostgreSQL database for the NETRA Cybersecurity Intelligence Platform.

---

## 1. Automated Setup (PowerShell)

NETRA provides an automated diagnostic and initialization script located at:
```powershell
scripts/setup-postgres.ps1
```

To run the script:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-postgres.ps1
```

### What this script does:
1. Tests TCP connectivity to `localhost:5432`.
2. Inspects local Windows services for PostgreSQL (`Get-Service *postgres*`) and attempts to start it if stopped.
3. If not installed, provides one-click `winget` or `chocolatey` installation commands.
4. Uses `psql` to automatically create the `netra` database if it does not already exist.

---

## 2. Installation Options on Windows

### Option A: Via Windows Package Manager (winget — Recommended)
Run in an elevated PowerShell terminal:
```powershell
winget install --id PostgreSQL.PostgreSQL.16 --exact --accept-package-agreements --accept-source-agreements
```

### Option B: Via Chocolatey
```powershell
choco install postgresql16 -y
```

### Option C: Official EnterpriseDB Installer
Download the Windows x86-64 installer from:
[EnterpriseDB PostgreSQL Downloads](https://www.enterprisedb.com/downloads/postgres-postgresql-downloads)

---

## 3. Database Credentials & Configuration

NETRA connects to PostgreSQL using the following defaults configured in `backend/src/main/resources/application.yml`:

| Property | Default Value | Environment Variable Override |
| :--- | :--- | :--- |
| **JDBC URL** | `jdbc:postgresql://localhost:5432/netra` | `SPRING_DATASOURCE_URL` |
| **Username** | `postgres` | `SPRING_DATASOURCE_USERNAME` |
| **Password** | `postgres` | `SPRING_DATASOURCE_PASSWORD` |
| **Driver** | `org.postgresql.Driver` | — |
| **Fail Timeout** | `-1` (resilient startup) | — |

To manually create the database using `psql`:
```bash
psql -U postgres -h localhost -p 5432 -c "CREATE DATABASE netra;"
```

---

## 4. Automatic Flyway Migrations

When PostgreSQL is active on port 5432, NETRA automatically applies all versioned migrations upon startup:
- `V1__initial_schema.sql`: 26 tables covering RBAC, networks, hosts, traffic flows, ML models, forecast runs, and incidents.
- `V2__seed_baseline_data.sql`: MITRE ATT&CK stages, default users, baseline models, and detection rules.

For standalone inspection or direct DBA execution, raw scripts are located at:
- `database/schema.sql`
- `database/seed.sql`

---

## 5. Offline Resiliency Behavior

If PostgreSQL is not running when the Spring Boot backend boots:
1. Spring Boot does **not** crash or abort.
2. HikariCP initializes in non-blocking mode (`initialization-fail-timeout: -1`).
3. Flyway logs a clear warning and defers migrations until the connection is established.
4. `/api/health` accurately reports `database.status = "DOWN"` with troubleshooting steps.
5. `/api/dashboard/summary` serves standby metrics without throwing 500 errors.
