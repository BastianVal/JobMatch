# Fase 3 — Catálogo de vacantes y búsqueda local

## Alcance entregado

- Vacante canónica con clave de identidad, empresa, rol, descripción, seniority, modalidad, contratación, salario, vigencia y versión.
- Publicaciones por fuente, URL normalizada, payload original y enlaces oficiales o agregadores.
- Ubicaciones, requisitos generales y habilidades requeridas con prioridad.
- Documento de búsqueda desnormalizado con `tsvector` e índices GIN y de filtros.
- Búsqueda de texto multirol con modalidad, contratación, ubicación, salario, antigüedad y exclusiones del perfil.
- Orden estable por relevancia, fecha de publicación y UUID; paginación mediante cursor opaco y máximo de 25 resultados.
- Detalle canónico que conserva todos los enlaces y evidencias de requisitos.
- Hasta 20 búsquedas guardadas por cuenta, aisladas por propietario y editadas con `If-Match`.
- Generador SQL idempotente y en conjunto para entre 1 y 1,000,000 de vacantes locales.
- Eliminación de búsquedas guardadas junto con los demás datos personales de la cuenta.

La navegación consulta exclusivamente PostgreSQL y nunca espera una fuente externa.

## API

Todos los endpoints requieren sesión autenticada:

```text
GET /api/v1/jobs/search
GET /api/v1/jobs/{id}
GET /api/v1/me/saved-searches
POST /api/v1/me/saved-searches
PUT /api/v1/me/saved-searches/{id}
DELETE /api/v1/me/saved-searches/{id}
```

Parámetros disponibles en búsqueda:

| Parámetro | Uso |
|---|---|
| `q` | Texto libre sobre título, empresa, descripción y habilidades. |
| `roleFamilyId` | UUID repetible; permite combinar hasta 10 roles. |
| `remoteMode` | `REMOTE`, `HYBRID` u `ONSITE`; repetible. |
| `employmentType` | `FULL_TIME`, `PART_TIME`, `CONTRACT` o `INTERNSHIP`; repetible. |
| `countryCode`, `place` | País y lugar. `place` busca una coincidencia contenida, normalizada sin acentos, tanto en ciudad como en estado. |
| `minimumMonthlySalary` | Requiere que el salario máximo conocido alcance el mínimo. |
| `publishedWithinDays` | Ventana entre 1 y 365 días. |
| `excludeEmployers` | Aplica las empresas excluidas del perfil cuando es `true`. |
| `limit` | Entre 1 y 25; por defecto 25. |
| `cursor` | Valor opaco recibido como `nextCursor`. |

Ejemplo multirol:

```text
GET /api/v1/jobs/search?q=Java&roleFamilyId=<rol-1>&roleFamilyId=<rol-2>&remoteMode=REMOTE&limit=20
```

Por ejemplo, `countryCode=MX&place=puebla` coincide con una ubicación como
`Puebla City`. Los parámetros `state` y `city` permanecen sólo como compatibilidad
temporal para búsquedas guardadas creadas antes de este cambio.

Una búsqueda guardada recibe un nombre y los mismos criterios, sin datos de paginación:

```json
{
  "name": "Backend remoto",
  "criteria": {
    "query": "Java Spring",
    "roleFamilyIds": ["11000000-0000-0000-0000-000000000001"],
    "remoteModes": ["REMOTE"],
    "countryCode": "MX"
  }
}
```

`POST` inicia en versión `1`. `PUT` y `DELETE` requieren `If-Match`; una versión obsoleta devuelve `409 VERSION_CONFLICT`.

## Fixtures y medición

Con el stack local levantado, genera o actualiza vacantes reproducibles:

```bash
scripts/generate-job-fixtures.sh 10000
```

El argumento permitido es `1..1000000`. El generador usa operaciones set-based, claves deterministas y `ON CONFLICT`, por lo que puede repetirse sin duplicar entidades. Incluye empresas, ubicaciones, publicaciones, enlaces, requisitos, habilidades y documentos de búsqueda.

Como comprobación de desarrollo, una consulta de texto sobre 10,000 vacantes locales devolvió 25 filas en aproximadamente 1.1 ms de ejecución dentro de PostgreSQL. Esta cifra es una referencia local, no un objetivo de producción; debe repetirse con un millón de activas durante el hardening.

## Evidencia automatizada

- `SearchCursorTest`: serialización reversible y rechazo de cursores manipulados.
- `DiscoveryServiceTest`: normalización de filtros, límite de página y máximo de búsquedas guardadas.
- `scripts/phase3-e2e.sh`: texto, multirol, cursor sin repetición, detalle, exclusiones y ciclo completo de búsquedas guardadas.
- Flyway V4 y el generador se ejecutan contra PostgreSQL 17 real.
