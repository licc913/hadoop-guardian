# Hadoop Guardian Linux Offline Package

## Overview

This package runs the backend jar and serves the built frontend on Linux.

Default mode:

- Java: use system `java`, or set `GuardianJavaHome`
- PostgreSQL: use the embedded PostgreSQL runtime under `runtime/postgresql`

Optional mode:

- Embedded Java runtime under `runtime/java`
- External PostgreSQL if you manually disable `GuardianUseEmbeddedPostgres`

## Prerequisites

Minimum prerequisites on the Linux server:

- `bash`
- `java` 8 or above, unless `runtime/java` is provided
- `tar`

## First Use

1. Extract the package.
2. Run:

```bash
chmod +x deploy.sh start.sh stop.sh status.sh scripts/*.sh
```

3. Edit `config/deploy-env.sh` if you need to change ports, accounts, CM, or LLM settings.
4. Run:

```bash
./deploy.sh
```

5. Open:

```text
http://<server-ip>:8080
```

## Files

- `deploy.sh`: initialize database and start the system
- `start.sh`: start backend and optional embedded PostgreSQL
- `stop.sh`: stop backend and optional embedded PostgreSQL
- `status.sh`: check runtime status
- `scripts/init-db.sh`: initialize schema and optional demo data
- `config/deploy-env.sh`: deployment settings

## Notes

- Frontend static files are served by the backend from the local `frontend` folder.
- This Linux package bundles a PostgreSQL runtime archive under `runtime/` and auto-unpacks it on first deploy.
- If you later place a Linux JRE under `runtime/java`, the scripts can use it.
