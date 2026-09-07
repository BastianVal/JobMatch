# Fase 7D — Explorar y detalle de vacantes

## Alcance entregado

- “Explorar” incorpora filtros interactivos de texto, roles múltiples, modalidad,
  tipo de empleo, país, estado, ciudad, salario mínimo, antigüedad y empresas
  excluidas del perfil.
- Las familias de roles se seleccionan únicamente desde el catálogo publicado;
  admite hasta diez y consulta sugerencias después de dos caracteres.
- Los filtros multivalor se envían como parámetros repetidos: OR dentro de cada
  grupo y AND entre grupos, tal como aplica la consulta SQL.
- El botón **Guardar** vive entre **Aplicar filtros** y **Limpiar**, evitando un
  formulario permanente. Al enfocar la barra, las búsquedas guardadas aparecen como
  sugerencias reutilizables y desaparecen al escribir o hacer clic fuera. Al aplicar
  una, su nombre queda visible dentro de la barra mientras sus filtros reales se
  conservan internamente. Desde las sugerencias también se pueden renombrar o eliminar.
- La página se presenta como lista de hasta 25 vacantes a la izquierda y detalle a
  la derecha. Se selecciona automáticamente la primera vacante de cada página.
- El detalle incluye descripción, requisitos, habilidades, ubicación, salario,
  fecha, fuentes y la acción para consultar evidencia de matching.
- La paginación utiliza el cursor estable del backend. Sólo permite volver a páginas
  ya visitadas o avanzar con el cursor siguiente; no muestra conteos inventados.
- La consulta SQL excluye siempre trabajos `DISCARDED` del usuario autenticado y la
  interfaz retira de inmediato una vacante al descartarla.

## Contratos utilizados

```http
GET /api/v1/jobs/search
GET /api/v1/jobs/{id}
POST /api/v1/me/saved-searches
GET /api/v1/me/saved-searches
PUT /api/v1/me/saved-searches/{id}
DELETE /api/v1/me/saved-searches/{id}
```

Los filtros que aceptan múltiples valores se repiten en la URL, por ejemplo:

```text
/api/v1/jobs/search?roleFamilyId=<uuid>&roleFamilyId=<uuid>&remoteMode=REMOTE&remoteMode=HYBRID
```

## Verificación

Los fixtures locales se distribuyen entre todas las familias de rol seleccionables. De esta manera, un perfil creado con el catálogo normalizado puede recibir recomendaciones durante las pruebas locales.

En escritorio, el detalle seleccionado de **Explorar** se mantiene visible mientras se recorre la lista. Cuando el contenido del detalle es más largo que la pantalla, usa su propio desplazamiento.

- Seis pruebas frontend y build TypeScript/Vite correctos usando Node de WSL.
- 36 pruebas backend correctas.
- `scripts/phase7-e2e.sh` valida búsqueda, detalle, búsqueda guardada, descarte
  excluido de Explorar, impresiones, concurrencia y ciclo de seguimiento contra el
  stack PostgreSQL real.
