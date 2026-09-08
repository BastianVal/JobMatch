# Prueba con bolsas públicas de empresas

JobMatch no pedirá al usuario la URL de una bolsa de empleo. La plataforma
mantiene un catálogo interno de bolsas públicas, identificado por proveedor e
identificador de bolsa. Un trabajo de fondo sincroniza cada bolsa y los
usuarios solamente buscan y filtran las vacantes ya normalizadas.

La primera lista verificada es:

| Proveedor | Empresa | Identificador de bolsa |
| --- | --- | --- |
| Greenhouse | C3 AI | `c3iot` |
| Greenhouse | CookUnity | `cookunity` |
| Lever | Bluelight Consulting | `bluelightconsulting` |
| Lever | Coupa | `coupa` |
| Ashby | Delinea | `delinea` |
| Ashby | Belvo | `belvo` |

Greenhouse, Lever y Ashby se habilitan explícitamente por bolsa después de
pasar las pruebas de contrato, normalización e ingesta local. Sus conectores
aceptan sólo claves incluidas en este catálogo; no se derivan de una búsqueda
del usuario. Se descartan las ubicaciones cuya pertenencia a México no pueda
demostrarse con el dato publicado, para no etiquetar una vacante extranjera
como mexicana por una configuración predeterminada.

Las publicaciones locales de carga (`LOCAL_FIXTURES`) no se muestran en una
instalación usada para explorar. Se pueden cerrar de forma reversible con
`sh scripts/retire-local-fixtures.sh --confirm` y regenerar exclusivamente
para pruebas de rendimiento con `scripts/generate-job-fixtures.sh`.

Los agregadores que requieren credenciales (Jooble y Adzuna) siguen siendo una
integración distinta: no forman parte de este ensayo de bolsas públicas y no
se activarán sin sus claves de cuenta.
