# Fase 7C — Recomendaciones derivadas del perfil

## Alcance entregado

- `Para ti` no incluye filtros interactivos: utiliza exclusivamente el perfil
  confirmado y el catálogo publicado.
- El guardado del perfil incrementa su versión y encola de forma durable un recálculo
  `REFRESH_RECOMMENDATIONS` en el worker.
- Mientras la proyección no corresponde a la versión vigente, la API responde
  `UPDATING` sin devolver resultados obsoletos y React consulta de nuevo en segundo
  plano. Al finalizar, cambia a `READY` y sustituye la lista.
- Los candidatos respetan roles objetivo, prioridad, modalidad, tipo de empleo,
  salario mínimo, seniority, experiencia, habilidades, idiomas y empresas excluidas.
- Sólo se consideran vacantes activas. Una vacante `DISCARDED` se excluye tanto del
  cálculo como de una proyección que ya hubiera sido generada.
- Se recuperan hasta 2,000 candidatas por cada rol objetivo, no 2,000 para el perfil
  completo. Los 500 resultados persistidos se intercalan entre las familias con
  candidatas, respetando la prioridad de los roles y el puntaje dentro de cada una.
- V14 y V15 invalidan las proyecciones derivadas con el límite y orden anteriores;
  el worker las reconstruye sin modificar el perfil del usuario.

## Compatibilidad y catálogo

V13 reclasifica vacantes que todavía apuntaban a las cuatro familias generales
retiradas, seleccionando de manera determinista el alias publicado más específico.
Las actualizaciones posteriores de conectores vuelven a resolver rol y seniority.

Los perfiles creados antes del catálogo v2 pueden conservar sus roles históricos al
editar otros campos o agregar roles actuales. Un UUID histórico no puede agregarse a
otro perfil. Esto evita el `400 Bad Request` que ocurría al reenviar el agregado
completo desde la UI sin relajar la autoridad del catálogo.

## Contrato de lectura

```http
GET /api/v1/me/recommendations?limit=25
```

La respuesta incluye `status`, `profileVersion`, `generatedProfileVersion`,
`generatedAt` e `items`. `items` está vacío durante `UPDATING`; el cliente nunca mezcla
recomendaciones de dos versiones del perfil.

## Verificación

- 36 pruebas backend, incluidas las transiciones `UPDATING`/`READY` y el encolado tras
  guardar el perfil.
- Build TypeScript/Vite y seis pruebas frontend.
- `phase2-e2e.sh`: catálogo, guardado del perfil, bloqueo optimista y validación.
- `phase6-e2e.sh`: facts, evidencias, caché versionado, recomendación por rol,
  actualización asíncrona, descarte y exclusión de empresa sobre PostgreSQL real.
- Verificación local con dos roles: los primeros diez resultados alternan
  `Desarrollador Frontend` y `Desarrollador Java`.
