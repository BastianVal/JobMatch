# ADR-004: deduplicación conservadora y escalonada

- Estado: aceptado
- Fecha: 2026-09-04

## Decisión

Resolver primero IDs y URLs exactos, después una clave determinista y finalmente similitud fuzzy acotada. Solo consolidar automáticamente desde 0.92; los casos ambiguos permanecen separados y auditados.

## Consecuencias

Se priorizan falsos negativos sobre fusiones incorrectas. Toda fusión será explicable y reversible mediante auditoría.

