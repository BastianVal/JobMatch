# Fase 4 — Ingesta y deduplicación

## Alcance entregado

- Consultas por fuente compartidas entre cuentas y demandas personales separadas; Greenhouse, Lever y Ashby ignoran ubicación al construir la firma porque sus contratos no la admiten.
- Solicitudes manuales con enfriamiento de 15 minutos y planificación periódica cada seis horas con jitter determinista.
- Trabajo durable `SYNC_CONNECTOR`, máximo de tres intentos y reintento con espera exponencial y jitter.
- Estado independiente por fuente con cuota diaria, contador de fallos, leases por fuente y consulta, y circuito abierto durante 15 minutos después de cinco fallos consecutivos.
- Adaptadores anticorrupción separados para Jooble, Adzuna, Greenhouse, Lever y Ashby. En local usan simuladores deterministas reemplazables y no requieren red ni credenciales externas.
- Normalización de texto, ubicación, enums y URL; eliminación de parámetros de rastreo y conservación del payload original con hash.
- Coincidencias exactas por identificador o URL y clave de identidad determinista protegida con bloqueo transaccional.
- Candidatos difusos ponderados: título 30%, empresa 25%, descripción 25%, ubicación 10% y salario/fecha 10%.
- Consolidación automática desde `0.92` cuando título y empresa también superan sus mínimos; entre `0.80` y `0.9199` se conserva una vacante separada y se registra la decisión.
- Bitácora por corrida, contadores por resultado, errores seguros por elemento, auditoría de merges y eventos `JobChanged` en outbox.
- Vigencia por consulta: sólo una respuesta declarada completa cuenta ausencias; después de tres ausencias exitosas y siete días se marca la publicación como faltante. La vacante queda `SUSPECTED_EXPIRED` únicamente cuando ya no tiene enlaces activos.
- Eliminación de demandas y solicitudes personales al eliminar la cuenta; las consultas y vacantes compartidas permanecen disponibles para otras cuentas.

La ingesta se ejecuta en el worker. Búsqueda y detalle siguen leyendo exclusivamente de PostgreSQL, por lo que nunca esperan a un conector.

## API

Ambos endpoints requieren sesión; `POST` también requiere CSRF:

```text
POST /api/v1/job-refreshes
GET  /api/v1/job-refreshes/{id}
```

Solicitud por texto y ubicación:

```json
{
  "query": "Backend Java",
  "location": "Ciudad de México"
}
```

También puede enviarse `roleFamilyId`. Debe existir al menos un rol o texto de búsqueda. `POST` responde `202 Accepted`, incluye `Location` y devuelve el estado:

```json
{
  "id": "<uuid>",
  "status": "SCHEDULED",
  "scheduledConnectors": 5,
  "completedConnectors": 0,
  "failedConnectors": 0,
  "nextAllowedAt": "<instant>",
  "createdAt": "<instant>"
}
```

Los estados terminales son `COMPLETED`, `PARTIAL` y `FAILED`. Una repetición dentro del enfriamiento devuelve `COOLDOWN` con cero conectores programados.

## Simulación local

Cada adaptador produce una vacante Backend compartida y una vacante propia. La compartida permite comprobar que cinco publicaciones terminan enlazadas a una sola vacante canónica.

Para provocar una falla aislada se usa como prefijo `FAIL_<FUENTE>`, por ejemplo `FAIL_JOOBLE_demo`. Esto existe únicamente en los simuladores locales.

## Evidencia automatizada

- `PostingNormalizerTest`: canonicalización de texto y URL, hashes y clave de identidad estable.
- `DeduplicationPolicyTest`: ponderaciones y límites de consolidación automática o separación auditable.
- `ConnectorContractTest`: contrato normalizable de los cinco adaptadores y aislamiento de una fuente fallida.
- `scripts/phase4-e2e.sh`: sesión real, cola, cinco fuentes, consolidación, cooldown, reintentos, resultado parcial y navegación disponible.
- Flyway V5, V6 y V7 se aplican contra PostgreSQL 17 real.

Con el stack levantado, la prueba integral se ejecuta desde la raíz:

```bash
docker run --rm --network jobmatch-local_private \
  --mount type=bind,source="$(pwd)/scripts/phase4-e2e.sh",target=/test.sh,readonly \
  curlimages/curl:8.16.0 sh /test.sh
```
