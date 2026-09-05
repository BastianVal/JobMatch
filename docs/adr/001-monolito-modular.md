# ADR-001: monolito modular con roles de ejecución

- Estado: aceptado
- Fecha: 2026-09-04

## Contexto

El producto necesita aislar navegación y trabajo pesado sin asumir la complejidad operativa de microservicios.

## Decisión

Usar un único artefacto Spring Boot con Clean Architecture por módulo. Se ejecuta en procesos `api`, `worker` y `migrate`, comparte PostgreSQL únicamente mediante contratos persistidos y nunca memoria de proceso.

## Consecuencias

El despliegue y las transacciones permanecen simples. Las fronteras se verifican con pruebas de arquitectura; separar un módulo en el futuro exige sustituir adapters, no reescribir dominio.

