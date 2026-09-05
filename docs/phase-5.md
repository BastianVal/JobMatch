# Fase 5 — Importación de CV

## Alcance entregado

- Almacenamiento fuera del árbol público con nombre UUID interno, nombre original sólo como metadato, SHA-256, tamaño y MIME detectado por firma.
- Límite de 5 MB, máximo de cinco documentos activos por cuenta, rechazo de extensiones distintas de PDF/DOCX y defensas contra DOCX altamente comprimidos.
- Volumen compartido únicamente por API y worker. La imagen crea el directorio con permisos del usuario no privilegiado; producción usa `/srv/jobmatch/cv`.
- Extracción Apache Tika 3.3.2 dentro del proceso `worker`, con límite de texto, timeout de 60 segundos y límites de CPU/memoria del contenedor.
- Normalización determinista de trayectoria, educación, certificaciones, idiomas y habilidades de catálogo. Las propuestas tienen versión de payload, fingerprint y evidencia hacia las trayectorias extraídas.
- Ejecuciones con estados `QUEUED`, `PROCESSING`, `READY`, `CONFIRMED`, `FAILED` y `EXPIRED`; los errores expuestos son códigos seguros y las propuestas vencen en 30 días.
- Detección exacta de duplicados contra el perfil vigente. Cada caso exige `KEEP_BOTH`, `REPLACE_EXISTING` o `SKIP`; habilidades e idiomas no admiten conservar dos entradas equivalentes.
- Ningún dato se acepta al subir el archivo. Todas las propuestas nacen `PENDING`, tienen versión optimista y deben quedar aceptadas o descartadas antes de confirmar.
- Confirmación idempotente y transaccional: bloquea la importación, comprueba la versión base del perfil, aplica todas las decisiones o ninguna, recalcula meses de habilidades y publica el cambio mediante el flujo existente de perfil/outbox.
- Eliminación lógica del documento y borrado físico del objeto. La eliminación de cuenta también retira todos sus archivos.

La extracción estructurada reconoce líneas en español o inglés con separadores `|`, por ejemplo:

```text
EXPERIENCE: Backend Developer | Example Corp | 2022-01 | 2024-06
EDUCATION: Computer Engineering | UNAM | 2017 | 2021
CERTIFICATION: Spring Professional | VMware | 2025
LANGUAGE: es | Español | NATIVE
SKILLS: Java, Spring Boot and PostgreSQL
```

Tika obtiene el texto de documentos reales; las reglas posteriores son deliberadamente deterministas y versionadas como `tika-3.3.2-rules-1`.

## API

Todos los endpoints requieren sesión y las mutaciones requieren CSRF:

```text
POST   /api/v1/me/cv-imports
GET    /api/v1/me/cv-imports/{id}
PUT    /api/v1/me/cv-imports/{id}/candidates/{candidateId}
POST   /api/v1/me/cv-imports/{id}/confirm
DELETE /api/v1/me/cv-documents/{documentId}
```

La subida es `multipart/form-data` con una parte `file` y responde `202 Accepted` más `Location`. El cliente consulta la ejecución hasta `READY` o `FAILED`.

Cada decisión usa `If-Match` con `decisionVersion`:

```json
{
  "decision": "ACCEPTED",
  "duplicateResolution": "REPLACE_EXISTING"
}
```

Para descartar basta `{"decision":"SKIPPED"}`; si existe un duplicado se registra automáticamente la resolución `SKIP`. Confirmar con propuestas pendientes, una resolución faltante o una versión base obsoleta responde `409` sin modificar el perfil.

## Persistencia y operación

Flyway V8 crea el esquema `cvimport` y separa archivo, documento presentado, ejecución, propuesta y caso de duplicado. Los UUID son los únicos identificadores públicos.

En despliegues con bind mount se debe crear `/srv/jobmatch/cv` con propietario `100:101` antes de levantar API y worker. El directorio forma parte de la política de backup y de restauración junto con PostgreSQL; una copia de sólo uno de los dos componentes queda incompleta.

## Evidencia automatizada

- `CvTextExtractorTest`: genera PDF textual y DOCX reales y comprueba las cinco clases de propuesta, fechas y evidencias.
- `FileSystemCvStorageAdapterTest`: nombre opaco, hash, MIME, extensión falsa, límite de tamaño y DOCX altamente comprimido.
- `scripts/phase5-e2e.sh`: sesión real, perfil base, extracción asíncrona de ambos formatos, cero aceptación implícita, duplicados, reemplazo, descarte, confirmación idempotente, recálculo, archivo falso y limpieza.
- Flyway V8 se aplica contra PostgreSQL 17 real.

Con el stack levantado, la prueba integral se ejecuta desde la raíz:

```bash
docker run --rm --network jobmatch-local_private \
  -v "$(pwd)/scripts:/workspace/scripts:ro" \
  curlimages/curl:8.16.0 sh /workspace/scripts/phase5-e2e.sh
```
