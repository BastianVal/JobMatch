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
