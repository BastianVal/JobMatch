# Fase 6 — Matching y recomendaciones

## Alcance entregado

- Extractor determinista `facts-1` que combina los requisitos normalizados de la vacante con alias del catálogo y texto limpio. Produce rol, seniority, experiencia profesional, tecnologías, idiomas y responsabilidades.
- IDs deterministas para facts inferidos y snapshots inmutables por versión de vacante, catálogo y extractor.
- Estrategia `score-1`, sin red ni datos no persistidos, con seis componentes y máximo total de 100 puntos.
- Razones `MATCH`, `GAP` y `CONSIDERATION`. Cada razón persiste el snapshot del requisito y el snapshot de la evidencia del perfil, incluidos UUID, meses y trayectorias cuando aplican.
- Los datos ausentes producen consideraciones neutrales identificables; nunca se convierten en requisitos inventados.
- Topes de 64 por tecnología o idioma obligatorio ausente y por cobertura profesional menor a 70%; tope de 44 para perfil Junior ante vacante Senior/Lead/Manager/Director, salvo que cubra la experiencia profesional explícita.
- Caché única por perfil, vacante y las versiones de perfil, vacante, catálogo y algoritmo. Una versión nueva crea una entrada distinta sin sobrescribir la explicación anterior.
- Consulta de resultados históricos por UUID mientras permanecen en la ventana operativa, permitiendo reconstruir el puntaje después de cambios del perfil.
- Recomendaciones exclusivamente para roles objetivo: máximo 2,000 candidatos recuperados y 500 resultados ordenados persistidos por versión de perfil.
- Regeneración proactiva en el worker, en lotes por perfil y con barrido cada 60 segundos para versiones nuevas o vacantes modificadas del mismo rol.
- Empresa excluida, modalidad, tipo de empleo y salario mínimo se filtran en SQL antes de puntuar recomendaciones; los valores ausentes no satisfacen un filtro activado.
- Limpieza semanal en el worker para snapshots obsoletos. La invalidación es incremental por clave: cambiar un perfil no invalida los resultados de otros perfiles y cambiar una vacante no afecta las demás.

## Componentes

| Componente | Máximo |
|---|---:|
| Rol y responsabilidades | 25 |
| Tecnologías y conocimientos | 25 |
| Seniority y experiencia | 20 |
| Proyectos relevantes | 15 |
| Tipo y solidez de experiencia | 10 |
| Preferencias, vigencia y calidad | 5 |

Dentro del componente técnico, tecnologías obligatorias reciben 70%, deseables 20% y responsabilidades 10%. La experiencia profesional se calcula como meses calendario únicos de `EMPLOYMENT`; proyectos y demás contextos conservan las evidencias y ponderaciones calculadas por el perfil.

## API

Los endpoints requieren una sesión autenticada:

```text
GET /api/v1/jobs/{jobId}/match
GET /api/v1/matches/{matchResultId}
GET /api/v1/me/recommendations?limit=50
```

La evaluación devuelve la clave completa de versión, puntaje, clasificación, componentes, razones y el indicador `cached`. La segunda consulta con la misma clave devuelve el mismo UUID y `cached: true`.

Las clasificaciones son `EXCELLENT` (85–100), `STRONG` (70–84.99), `POSSIBLE` (50–69.99) y `LOW` (0–49.99). Sólo se incluyen secciones que contienen razones.

## Persistencia

Flyway V9 crea:

- `matching.job_fact_snapshot`
- `matching.match_result`
- `matching.match_reason`
- `matching.recommendation`

La unicidad de `match_result` incluye `(profile_id, canonical_job_id, profile_version, job_version, catalog_version, scoring_version)`. `match_reason` guarda JSONB separado para requisito y evidencia, además de los puntos que aportó; por eso el resultado no depende de consultar el estado actual del perfil para explicarse.

## Evidencia automatizada

- `JobFactExtractorTest`: skills, idioma, años profesionales e IDs deterministas.
- `DeterministicScoringV1Test`: determinismo, evidencia concreta, gap obligatorio y ambos topes.
- `scripts/phase6-e2e.sh`: cuenta y perfil reales, refresh, facts, primer cálculo, acierto de caché, recomendaciones por rol, nueva versión de perfil, lectura histórica y exclusión de empresa.
- Flyway V9 se aplica sobre PostgreSQL 17 real.

Con el stack levantado:

```bash
docker run --rm --network jobmatch-local_private \
  -v "$(pwd)/scripts:/workspace/scripts:ro" \
  curlimages/curl:8.16.0 sh /workspace/scripts/phase6-e2e.sh
```
