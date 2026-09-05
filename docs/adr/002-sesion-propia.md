# ADR-002: autenticación con Spring Security y sesión JDBC

- Estado: aceptado
- Fecha: 2026-09-04

## Decisión

La SPA first-party usará una cookie opaca y Spring Session JDBC. CSRF será sincronizado por sesión. Auth0 no forma parte del MVP.

## Consecuencias

Las sesiones pueden revocarse centralmente y sobreviven reinicios. El equipo asume el flujo de correo, recuperación, rate limiting y protección de credenciales en la Fase 1.

