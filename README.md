# NETRA — Network Threat Early-warning & Risk Analytics
### SIH 2026 — SIH26153 — NTRO
**See the attack forming. Predict what comes next.**

---

## Architecture Overview

NETRA is a local cybersecurity intelligence and predictive threat forecasting platform built with:
- **Backend:** Java 21 / 25, Spring Boot 3.4.1, Spring Security, Spring Data JPA, STOMP WebSocket.
- **Authoritative Database:** PostgreSQL 16+ with versioned Flyway migrations (`V1`–`V4`).
- **Frontend:** React 19, Vite 8, TypeScript, Lucide Icons, Glassmorphism Dark SOC theme.
- **Machine Learning Engine:** Python FastAPI with PyTorch (TCN / LSTM architectures).

---

## Quick Start Guide

### 1. Prerequisites Check
Ensure Java 21+ and Node 20+ are installed:
```powershell
java -version
node -v
npm -v
```

### 2. PostgreSQL Database Setup
NETRA uses PostgreSQL as its authoritative database. To inspect or start your local database:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-postgres.ps1
```
*Note: If PostgreSQL is not yet running, the backend will still start in resilient standby mode without crashing.*

### 3. Start the ML Forecasting Service
In a separate terminal:
```powershell
cd ml-service
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```
Confirm it is ready at `http://localhost:8000/health`.

### 4. Start Spring Boot Backend
From the repository root:
```powershell
cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\mvnw.cmd spring-boot:run
```
The backend starts at `http://localhost:8080`.
- Health Endpoint: `http://localhost:8080/api/health`
- Dashboard API: `http://localhost:8080/api/dashboard/summary`
- Actuator: `http://localhost:8080/actuator/health`

### 5. Start React Frontend
In a separate terminal:
```powershell
cd frontend
npm.cmd run dev
```
Open `http://localhost:5173` in your browser to access the NETRA Command Centre.

### 6. Running Tests
To run backend unit tests:
```powershell
cd backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\mvnw.cmd test
```
To run frontend type-check & build:
```powershell
cd frontend
npm.cmd run build
```
To run the Python ML checks:
```powershell
cd ml-service
.\.venv\Scripts\pytest.exe tests/test_ml_service.py
```

## Current Verification

The full local stack has been verified with PostgreSQL persistence enabled: Flyway migrations V1–V4, the Java backend, the React build, and the ML service health endpoint all pass. See [docs/FINAL_VERIFICATION.md](docs/FINAL_VERIFICATION.md) for the current verification record and known academic limitations.
