# ADR-003: cola durable y outbox en PostgreSQL

- Estado: aceptado
- Fecha: 2026-09-04

## Decisión

Persistir tareas en `ops.background_task`, reclamarlas con `FOR UPDATE SKIP LOCKED`, lease e intentos, y persistir eventos en `ops.outbox_event` dentro de la transacción de negocio.

## Consecuencias

No se opera un broker adicional. Los handlers deben ser idempotentes y la capacidad de PostgreSQL deberá vigilarse antes de escalar workers horizontalmente.

