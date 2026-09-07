# Fase 8 — Operación y despliegue de producción

## Alcance de esta primera entrega

- Los servicios de producción tienen límites de CPU y memoria configurables por
  variables de entorno. Los valores de `.env.example` son un punto de partida,
  no una recomendación universal para una instancia EC2.
- La API termina el proceso ante falta de memoria y calcula su heap a partir del
  límite del contenedor; así Docker y la JVM no compiten por la RAM disponible.
- `/actuator/health/liveness` y `/actuator/health/readiness` se pueden consultar
  a través de Caddy. Las métricas permanecen sólo en la red privada: Caddy no
  expone `/actuator/metrics` al público.
- Se incluyen scripts de respaldo y restauración de PostgreSQL con checksum.

## Respaldo diario

En el host EC2, desde el directorio del despliegue:

```sh
BACKUP_DIR=/srv/jobmatch/backups ./scripts/backup-postgres.sh
```

El resultado es un `pg_dump -Fc` y su archivo `.sha256`. El directorio de copias
no se versiona en Git. Programa esta orden diariamente con `cron` o systemd y
conserva siete copias diarias y cuatro semanales fuera de la instancia.

## Restauración ensayada

Una restauración reemplaza datos. Hazla primero en una instancia aislada o en una
base de datos vacía. Detén `api` y `worker`, verifica el checksum y confirma de
forma explícita:

```sh
docker compose stop api worker
./scripts/restore-postgres.sh /srv/jobmatch/backups/jobmatch-AAAAmmddTHHMMSSZ.dump --confirm
docker compose up -d migrate api worker
curl --fail https://"$APP_HOST"/actuator/health/readiness
```

Después valida que una cuenta, una vacante y una relación de seguimiento estén
presentes. Sólo entonces se debe volver a dirigir tráfico al servicio.

## Límites y health checks

Antes de publicar, ajusta `POSTGRES_*`, `API_*` y `WORKER_*` al tamaño real de
EC2. Se debe dejar RAM libre para el sistema operativo, Caddy y los picos de
PostgreSQL. Los endpoints de health sirven para el balanceador y las revisiones
operativas; no contienen detalles sensibles.

## Variables de producción

Parte de `deploy/production.env.example`, cópialo fuera del repositorio como
`/srv/jobmatch/.env` y sustituye todos sus valores. En particular, el tag de
imagen debe ser un SHA inmutable y las contraseñas, secretos y credenciales SMTP
no deben estar en Git ni en el historial de comandos.

La superposición `compose.prod.yaml` no permite `build`: API, worker y migraciones
usan `JOBMATCH_BACKEND_IMAGE`; la SPA usa `JOBMATCH_FRONTEND_IMAGE`. Esto evita
que el host EC2 compile código y asegura que todos los roles de backend corran el
mismo artefacto identificado por `JOBMATCH_IMAGE_TAG`.

## Pendiente de las siguientes entregas de Fase 8

1. Pruebas de carga y revisión de índices con `EXPLAIN ANALYZE`.
2. Métricas operativas de negocio, alertas y runbooks de fallas.
3. Pipeline de entrega con imagen inmutable, migración y rollback ensayado.
4. Revisión OWASP, eliminación integral de cuenta y ensayo de restauración.
5. Prueba de integración limitada con las APIs reales autorizadas, en staging.
