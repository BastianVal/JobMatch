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

## Entrega continua e imágenes

`.github/workflows/ci.yml` ejecuta backend, frontend y la validación de Compose en
cada pull request y cambio a `main`. `.github/workflows/release.yml` es manual:
publica las imágenes de backend y frontend en Docker Hub con un tag inmutable y,
si se activa `deploy`, ejecuta primero la migración y después actualiza EC2.

Antes de activar el release se deben registrar estos secretos de GitHub:

- `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN`.
- `EC2_HOST`, `EC2_USER` y `EC2_SSH_KEY`.

La instancia debe contener el repositorio en `/srv/jobmatch`, Docker Compose y el
archivo `/srv/jobmatch/.env` basado en la plantilla. Para rollback, se ejecuta el
mismo workflow indicando el SHA inmutable de la versión anterior.

## Prueba de carga de búsqueda

`tests/load/search.js` usa k6 y prueba solamente la búsqueda local autenticada;
no consulta conectores ni APIs de terceros. Obtén una cookie de una cuenta de
pruebas y ejecuta en staging:

```sh
K6_BASE_URL=https://staging.example.com \
K6_SESSION_COOKIE='valor-de-la-cookie' \
K6_SESSION_COOKIE_NAME='__Host-jobmatch_session' \
k6 run tests/load/search.js
```

Por defecto son 50 usuarios virtuales durante dos minutos y la prueba falla si el
p95 de la búsqueda supera 500 ms o si hay 1% o más de errores. Después se revisan
las consultas lentas con `EXPLAIN ANALYZE` antes de cambiar índices.

## Pendiente de las siguientes entregas de Fase 8

1. Ejecutar la carga contra un staging con datos representativos y revisar índices
   con `EXPLAIN ANALYZE`.
2. Métricas operativas de negocio, alertas y runbooks de fallas.
3. Revisar OWASP, eliminación integral de cuenta y ensayar la restauración.
4. Prueba de integración limitada con las APIs reales autorizadas, en staging.
