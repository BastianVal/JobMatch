# Fase 2 — Catálogo y perfil manual

## Alcance entregado

- Catálogo versionado de familias de roles y habilidades, con aliases normalizados y semillas reproducibles en Flyway.
- Perfil profesional 1:1 con la cuenta: encabezado, resumen, ubicación, seniority, preferencias y empresas excluidas.
- Hasta 10 roles objetivo ordenados por prioridad.
- Trayectoria, educación, cursos y certificaciones, idiomas y habilidades.
- Distinción persistida entre servicio social técnico y voluntariado técnico.
- Habilidades ligadas al catálogo o personalizadas, nunca ambas; las personalizadas no participan en matching.
- Evidencias de habilidades ligadas a la trayectoria y proyecciones de meses profesionales y prácticos ponderados.
- Edición optimista mediante `If-Match`; una versión obsoleta devuelve `409 VERSION_CONFLICT`.
- Evento `ProfileChanged` en el outbox dentro de la misma transacción que cada edición.
- Eliminación de todos los datos del perfil al eliminar la cuenta.

## API

Todos los endpoints requieren una sesión autenticada. Las mutaciones también requieren el token CSRF descrito en la Fase 1.

Catálogos:

```text
GET /api/v1/catalog/roles?q=backend&limit=20
GET /api/v1/catalog/skills?q=java&limit=20
```

Perfil completo y secciones editables:

```text
GET /api/v1/me/profile
PUT /api/v1/me/profile
GET|PUT /api/v1/me/target-roles
GET|PUT /api/v1/me/trajectory
GET|PUT /api/v1/me/skills
```

Un perfil nuevo se obtiene con versión `0`. Cada `PUT` debe enviar esa versión en `If-Match`, por ejemplo `If-Match: "0"`. La respuesta incluye la nueva versión tanto en el cuerpo como en `ETag`. Las rutas de sección conservan el resto del agregado y también incrementan su versión global.

Los identificadores enviados por el cliente son UUID públicos. Para crear elementos nuevos puede omitirse su `id`; el servidor lo asigna. Una habilidad acepta `catalogSkillId` o `customName`, y `evidenceTrajectoryIds` solo puede contener elementos de trayectoria del mismo perfil.

## Cálculo de experiencia

Cada mes calendario se cuenta una sola vez por habilidad. Si existen evidencias simultáneas, se conserva el mayor peso de ese mes:

| Contexto | Peso | Profesional |
|---|---:|:---:|
| Empleo | 100% | Sí |
| Prácticas | 70% | Sí |
| Servicio social técnico | 60% | No |
| Voluntariado técnico | 55% | No |
| Proyecto personal / open source | 50% | No |
| Proyecto académico | 35% | No |
| Estudio | 10% | No |

`professionalMonths` cuenta meses distintos de empleo y prácticas sin ponderarlos. `weightedPracticalMonths` suma, con precisión decimal, el mayor peso disponible en cada mes. Las evidencias guardan también una instantánea de su contexto para que el resultado sea auditable.

## Evidencia automatizada

- `SkillDurationCalculatorTest`: meses solapados, máximo mensual y pesos distintos para servicio social y voluntariado.
- `ProfileServiceTest`: normalización, deduplicación, proyecciones, outbox y rechazo de evidencia ajena.
- `scripts/phase2-e2e.sh`: catálogo, perfil completo, evidencias, proyecciones, conflicto de versión, endpoints seccionales y limpieza al eliminar la cuenta contra el stack real.

La comprobación E2E se ejecuta desde la raíz con el stack local levantado:

```bash
docker run --rm --network jobmatch-local_private \
  --mount type=bind,source="$(pwd)/scripts/phase2-e2e.sh",target=/test.sh,readonly \
  curlimages/curl:8.16.0 sh /test.sh
```
