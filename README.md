# JobMatch México

Fundación del monolito modular descrito en `PLAN.md`. El mismo artefacto Spring Boot se ejecuta como `api`, `worker` o `migrate`; PostgreSQL coordina el trabajo durable y Caddy es el único punto de entrada.

## Arranque local

1. Copia `.env.example` a `.env`.
2. Ejecuta `docker compose up --build`.
3. Abre `http://localhost:8090`.

Comprobaciones útiles:

```bash
curl http://localhost:8090/actuator/health/readiness
curl -i -X POST http://localhost:8090/api/v1/operations/demo-tasks \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{"message":"hola"}'
```

Repetir la segunda llamada con la misma clave devuelve la misma tarea. El worker la reclama con lease y la marca como completada. Para inspeccionarla:

```bash
curl http://localhost:8090/api/v1/operations/demo-tasks/<publicId>
```

## Desarrollo

- Backend: Java 21 y Maven (`cd backend && mvn test`).
- Frontend: Node 22 (`cd frontend && npm install && npm test && npm run build`).
- Integración: `docker compose -f compose.yaml -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from backend-tests`.

Las decisiones arquitectónicas están en [`docs/adr`](docs/adr/README.md) y los criterios de Fase 0 en [`docs/phase-0.md`](docs/phase-0.md).
