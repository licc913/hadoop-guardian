# Hadoop Guardian Intranet Package

## Quick Start

1. Edit `config\deploy-env.ps1` only if you want to change ports, default accounts, CM, or LLM settings.
2. Run `deploy.cmd` or `deploy.ps1`.
3. Open `http://localhost:8080`.

## What Is Included

- Embedded Java runtime under `runtime\java`
- Embedded PostgreSQL runtime under `runtime\postgresql`
- Backend jar under `app\hadoop-guardian-backend.jar`
- Frontend static assets under `frontend`
- Schema and demo data SQL under `sql`

## Files

- `deploy.cmd`: one-click launcher for Windows
- `deploy.ps1`: initialize database and start the system
- `scripts\start.ps1`: start the embedded database and backend service
- `scripts\stop.ps1`: stop the backend and embedded database
- `scripts\status.ps1`: check runtime status
- `scripts\init-db.ps1`: initialize schema and optional demo data

## Notes

- Frontend static files are served by the backend from the local `frontend` folder.
- By default the package starts its own PostgreSQL on `127.0.0.1:15432`.
- CM and LLM settings can be adjusted later in the web UI after login.
