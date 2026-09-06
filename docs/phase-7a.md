# Fase 7A — Catálogo normalizado de roles

## Alcance entregado

- Catálogo versión 2 con 42 familias seleccionables y 165 aliases tomados del listado acordado.
- Variantes en español, inglés, `Jr` y `Sr` resuelven a una familia; el seniority se conserva por separado.
- Las cuatro familias generales anteriores permanecen como compatibilidad para referencias existentes, pero ya no aparecen en búsquedas para selección.
- Búsqueda sencilla por texto contenido, sin distinguir mayúsculas, minúsculas o acentos.
- Resolución determinista de títulos de vacante por coincidencia exacta y alias más específico.
- Las vacantes sin una coincidencia confiable conservan familia nula; no se inventa una clasificación general.
- El cambio a versión 2 invalida naturalmente snapshots y resultados de matching creados con la versión anterior.

## Contratos

- `GET /api/v1/catalog/roles?q=java&limit=10` devuelve familias seleccionables coincidentes por nombre o alias.
- El perfil conserva el máximo de 10 roles objetivo y almacena UUID de familia, nunca texto libre.
- La ingesta usa `CatalogRoleResolver` y extrae seniority del título sólo cuando el conector no lo proporciona.

## Verificación

- `RoleTitleMatcherTest` cubre especificidad, normalización, límites de palabra y seniority.
- `JdbcCatalogRoleResolverIT` aplica todas las migraciones sobre PostgreSQL real y comprueba conteos, búsqueda y resolución.
