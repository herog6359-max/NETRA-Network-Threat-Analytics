# NETRA — Final Verification Record

**Project:** SIH26153 — AI-Based Network Attack Forecasting from Network Traffic Data  
**Verification date:** October 13, 2026  
**Runtime mode:** Local academic prototype using synthetic/authorised demonstration telemetry.

## Verified components

| Area | Verification | Result |
|---|---|---|
| PostgreSQL | Local PostgreSQL 16.6 with database `netra` | Connected and durable |
| Flyway | Schema history | V1–V4 applied successfully |
| Java backend | `mvnw.cmd test` under Java 25, targeting Java 21 | 15 tests passed; 0 failures/errors |
| Frontend | `npm.cmd run lint` and `npm.cmd run build` | Passed cleanly |
| Backend runtime | `GET /api/health` | 200; database UP and ML service UP |
| Database Studio | `GET /api/db-studio/schema-meta` | 200; live PostgreSQL metadata returned |
| Passive sensor adapter | `GET /api/telemetry/sensor/status` | 200; safely inactive until explicitly authorised |

## Local endpoints

- React dashboard: `http://127.0.0.1:5173`
- Spring Boot API: `http://127.0.0.1:8080/api/health`
- Spring Actuator: `http://127.0.0.1:8080/actuator/health`
- FastAPI model service: `http://127.0.0.1:8000/health`

## Honest scope statement

NETRA is a defensive academic prototype. It accepts authorised or synthetic flow telemetry and does not conduct active scanning. The deterministic Java engine is explicitly identifiable as a baseline; the Python TCN service reports its own model provenance. Forecasts are decision-support outputs, not proof that an attack will occur.

## Known limitations for viva

1. The default authentication configuration is development-oriented. It must be tightened before deployment beyond a classroom or controlled demo environment.
2. The passive sensor remains disabled until an operator authorises it through the API; it does not capture traffic automatically.
3. The Python test command should be run locally when presenting ML test evidence. The bundled virtual environment and live `/health` service are available.
