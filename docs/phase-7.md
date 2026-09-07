# Fase 7 — Seguimiento e integración UX

## Alcance entregado

- Impresiones por lote sobre vacantes realmente renderizadas. La clave `(cuenta, vacante)` hace el registro idempotente y conserva primera/última visualización.
- Indicador `new` derivado de la ausencia de impresión; un `GET` nunca modifica estado.
- Seguimiento personal con guardado, descarte, postulación, entrevista, oferta y resultados terminales.
- Historial inmutable de cada transición con estado anterior, estado nuevo, nota, versión y fecha.
- Concurrencia optimista mediante `If-Match`; una versión obsoleta devuelve `409 VERSION_CONFLICT`.
- Reintentos seguros mediante `Idempotency-Key`; reutilizar una clave con otro payload devuelve `409 IDEMPOTENCY_KEY_REUSED`.
- SPA React responsiva con autenticación, recomendaciones, búsqueda, evidencia de matching, seguimiento y perfil manual.
- Estados de carga, secciones vacías, reintento y recuperación de conflictos en el cliente.
- Eliminación de cuenta incluye impresiones, seguimiento e historial.
- Notificaciones únicas, accesibles y temporales: desaparecen automáticamente a los
  cinco segundos, reinician el temporizador ante un mensaje nuevo y se descartan al
  cambiar de sección.

## Máquina de estados

```text
NEW ──> SAVED ──> DISCARDED ──> SAVED
 └────> APPLIED ──> INTERVIEW ──> OFFER ──> ACCEPTED
             ├────────┬────────────┴───────> REJECTED
             └────────┴────────────────────> WITHDRAWN
```

`NEW` es una condición de presentación, no un estado de `user_job`. También se permite iniciar directamente en `DISCARDED` cuando la persona descarta una recomendación.

## API

- `POST /api/v1/me/job-impressions` recibe hasta 100 UUID en `jobIds`.
- `GET /api/v1/me/job-activity?jobId=...` consulta en lote `new` y el estado personal.
- `GET /api/v1/me/jobs/{jobId}/tracking` devuelve el recurso y su historial.
- `PUT /api/v1/me/jobs/{jobId}/tracking` exige `If-Match` e `Idempotency-Key`.
- `GET /api/v1/me/tracking?state=&limit=` lista el seguimiento del propietario.

## Persistencia y verificación

La migración `V11__tracking.sql` crea `tracking.job_impression`, `tracking.user_job` y `tracking.user_job_event`, con unicidad por cuenta/vacante e índices por estado y actividad reciente.

`scripts/phase7-e2e.sh` cubre impresión, indicador de nueva, retry idempotente, colisión de clave, versión obsoleta, transición inválida, recorrido hasta estado terminal e historial reconstruible.

La implementación de notificaciones está documentada en [`phase-7g.md`](phase-7g.md).
