# ADR-005: matching determinista y versionado

- Estado: aceptado
- Fecha: 2026-09-04

## Decisión

Implementar estrategias puras versionadas. Cada resultado conserva versiones de perfil, vacante, catálogo y algoritmo, además de razones ligadas a requisitos y evidencias.

## Consecuencias

Un puntaje puede reconstruirse y probarse sin IA. Los cambios invalidan solo combinaciones afectadas y el caché no depende de tiempo ni de estado implícito.

