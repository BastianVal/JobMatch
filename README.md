# JobMatch México

Fundación del monolito modular descrito en `PLAN.md`. El mismo artefacto Spring Boot se ejecuta como `api`, `worker` o `migrate`; PostgreSQL coordina el trabajo durable y Caddy es el único punto de entrada.

## Arranque local

1. Copia `.env.example` a `.env`.
2. Ejecuta `docker compose up --build`.
3. Abre `http://localhost:8090`.

Comprobaciones útiles:

```bash
curl http://localhost:8090/actuator/health/readiness
```

Los endpoints de negocio requieren una sesión autenticada y protección CSRF; consulta el flujo de identidad más abajo.

## Desarrollo

- Backend: Java 21 y Maven (`cd backend && mvn test`).
- Frontend: Node 22 (`cd frontend && npm install && npm test && npm run build`).
- Integración: `docker compose -f compose.yaml -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from backend-tests`.

Las decisiones arquitectónicas están en [`docs/adr`](docs/adr/README.md) y los criterios de Fase 0 en [`docs/phase-0.md`](docs/phase-0.md).

## Identidad y acceso (Fase 1)

La API ofrece registro, verificación por correo, sesión propia con CSRF, recuperación de contraseña y eliminación de cuenta. El flujo y ejemplos están en [`docs/phase-1.md`](docs/phase-1.md).

## Catálogo y perfil manual (Fase 2)

La API permite consultar roles y habilidades normalizados y construir el perfil profesional completo sin importar un CV. El contrato, las reglas de concurrencia y el cálculo de experiencia están en [`docs/phase-2.md`](docs/phase-2.md).

## Vacantes y búsqueda local (Fase 3)

PostgreSQL almacena vacantes canónicas, sus publicaciones y la proyección de texto/filtros. La API ofrece búsqueda por cursor, detalle y búsquedas guardadas; consulta [`docs/phase-3.md`](docs/phase-3.md) para el contrato y el generador local de hasta un millón de fixtures.

## Ingesta y deduplicación (Fase 4)

El worker comparte consultas, procesa cinco fuentes de forma independiente y consolida publicaciones con reglas auditables de identidad, similitud y vigencia. El flujo local determinista, la API de actualización y las políticas operativas están en [`docs/phase-4.md`](docs/phase-4.md).

## Importación de CV (Fase 5)

PDF textuales y DOCX se almacenan con nombres internos aleatorios y se extraen en el worker. El usuario revisa cada propuesta y resuelve duplicados antes de una confirmación atómica que recalcula el perfil. El contrato, límites y prueba integral están en [`docs/phase-5.md`](docs/phase-5.md).

## Matching y recomendaciones (Fase 6)

El motor `score-1` produce puntajes deterministas por seis componentes y conserva snapshots de requisitos y evidencias. La caché se separa por las versiones de perfil, vacante, catálogo y algoritmo; las recomendaciones respetan roles objetivo, preferencias y exclusiones. Consulta [`docs/phase-6.md`](docs/phase-6.md).

## Seguimiento e integración UX (Fase 7)

La aplicación registra impresiones por lote, deriva el indicador de vacante nueva y permite guardar, descartar y seguir una postulación hasta su resultado final. Las transiciones conservan historial, usan `If-Match` para concurrencia optimista e `Idempotency-Key` para reintentos seguros.

La SPA React integra autenticación, perfil, CV, búsqueda, recomendaciones explicables y seguimiento, incluidos estados pendientes y errores recuperables. Consulta [`docs/phase-7.md`](docs/phase-7.md).
