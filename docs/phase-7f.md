# Fase 7F — Importación de CV desde Perfil

## Alcance entregado

- La opción independiente **Importar CV** se retiró de la navegación. En **Perfil**,
  un botón situado junto a **Editar perfil** abre un modal con el flujo completo.
- Al final del modal se muestran los documentos activos, con acciones para descargar
  el archivo original o eliminarlo. Eliminarlo no borra información ya confirmada.
- Se mantiene el límite de cinco documentos activos que aplica el backend. La UI lo
  comunica y bloquea una nueva carga al alcanzarlo.
- Tras cargar un PDF textual o DOCX, la interfaz consulta automáticamente el estado
  mientras está en cola o procesándose. El estado `READY` del contrato se presenta
  como **Listo para revisar**; ya no se espera el estado inexistente `REVIEW`.
- El extractor reconoce tanto el formato estructurado de pruebas como secciones
  habituales de CV en español o inglés. Propone empleo, prácticas/pasantías, servicio
  social, voluntariado, proyectos, educación, certificaciones, idiomas y habilidades
  cuando el documento aporta los datos y fechas necesarios, sin inventarlos.
- Las propuestas se muestran como campos legibles, omitiendo UUID, referencias de
  catálogo y códigos internos. Cada una
  puede aceptarse u omitirse; cuando se detecta un duplicado se puede conservar
  ambos, sustituir el existente u omitir la propuesta.
- El perfil no cambia al aceptar una propuesta. Sólo cambia al confirmar, y entonces
  se recargan inmediatamente el perfil y los documentos.

## Contratos utilizados

```http
POST   /api/v1/me/cv-imports
GET    /api/v1/me/cv-imports/{id}
PUT    /api/v1/me/cv-imports/{id}/candidates/{candidateId}
POST   /api/v1/me/cv-imports/{id}/confirm
GET    /api/v1/me/cv-documents
GET    /api/v1/me/cv-documents/{id}/download
DELETE /api/v1/me/cv-documents/{id}
```

## Verificación

- `npm test` y `npm run build` completan correctamente desde Node en WSL.
- Las pruebas del extractor incluyen un CV convencional con múltiples secciones.
- La prueba integral de API existente cubre búsqueda, búsquedas guardadas,
  descartes y seguimiento. La importación reutiliza los contratos ya verificados
  de la fase de backend y ahora tiene su flujo completo accesible desde Perfil.
