# NETRA Frontend

The NETRA command-centre user interface is a React 19, TypeScript, and Vite application.

## Run locally

```powershell
npm.cmd run dev
```

Open `http://127.0.0.1:5173`. The Vite proxy forwards `/api` and `/ws` requests to the Spring Boot backend at port 8080.

## Quality checks

```powershell
npm.cmd run lint
npm.cmd run build
```

The dashboard is intentionally driven by backend APIs; start PostgreSQL, the ML service, and the Java backend first using the root [README](../README.md).
