# Fase 7B — Perfil completo y selector de roles

## Alcance entregado

- Perfil en modo lectura por defecto, con el resumen completo y secciones separadas.
- Edición explícita mediante `Editar perfil`, con `Guardar cambios` y `Cancelar`.
- Edición de información profesional, preferencias, empresas excluidas, trayectoria,
  educación, certificaciones, idiomas, habilidades y evidencias.
- Selector de roles objetivo respaldado exclusivamente por el catálogo publicado:
  consulta contenida desde dos caracteres, debounce de 250 ms, máximo diez roles y
  prioridad reordenable.
- Validación en cliente junto a cada grupo y validación autoritativa en backend.
- El backend rechaza familias inactivas, inexistentes o no seleccionables. Como
  compatibilidad de migración, un perfil puede conservar una familia histórica que
  ya tenía seleccionada, pero no puede agregarla a un perfil nuevo.
- Lectura de los cinco CV activos más recientes dentro del perfil. La carga y revisión
  se integrarán en esta misma página durante la Fase 7F.

## Contratos relevantes

```http
GET /api/v1/me/profile
PUT /api/v1/me/profile
GET /api/v1/catalog/roles?q=java&limit=10
GET /api/v1/catalog/skills?q=java&limit=10
GET /api/v1/me/cv-documents
```

`PUT /me/profile` conserva el bloqueo optimista mediante `If-Match`. Los UUID y nombres
generados en el cliente no son autoridad de catálogo: persistencia resuelve la familia
por UUID y exige que esté activa y sea seleccionable, salvo una selección histórica ya
perteneciente al mismo perfil.

## Criterios de comprobación

1. Al entrar a Perfil no se muestran controles editables.
2. Cancelar descarta el borrador completo sin ejecutar una mutación.
3. El buscador no consulta antes de dos caracteres y nunca guarda texto libre como rol.
4. Los roles pueden quitarse y reordenarse; las prioridades enviadas son consecutivas.
5. El formulario no admite más de diez roles.
6. Tras guardar se vuelve a modo lectura y se muestra todo el contenido sin truncar.
7. La lista de CV sólo devuelve documentos activos del usuario autenticado, hasta cinco.
