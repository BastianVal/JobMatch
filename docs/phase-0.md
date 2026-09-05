# Fase 0 — criterios de salida

| Criterio | Evidencia |
|---|---|
| Artefacto único, roles separados | Perfiles Spring `api`, `worker`, `migrate` y `compose.yaml` |
| Entrada pública única | Caddy publica 80/443; la red de aplicación es interna |
| Migraciones y esquemas | Flyway `V1__platform_foundations.sql` |
| Problem Details | `ApiExceptionHandler` y prueba MVC |
| UUID públicos | API y tablas exponen `public_id`, no IDs internos |
| Cola durable | Endpoint demo, worker con lease y consulta `SKIP LOCKED` |
| Idempotencia | `ops.idempotency_record` y respuesta repetible |
| Outbox | `ops.outbox_event` y puerto de persistencia |
| Datos reproducibles | Migración de desarrollo `R__development_seed.sql` |
| PostgreSQL real en pruebas | Testcontainers en `BackgroundTaskRepositoryIT` |
| Health checks | Actuator liveness/readiness y health check de Compose |

