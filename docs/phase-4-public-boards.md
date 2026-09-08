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

Cada entrada inicia desactivada. No se harán llamadas externas ni se cargarán
datos simulados para esas entradas hasta que el conector de su proveedor haya
pasado las pruebas de contrato, normalización e ingesta en un entorno local.
Después, se habilitará de forma explícita por bolsa, no por una búsqueda de un
usuario.

Los agregadores que requieren credenciales (Jooble y Adzuna) siguen siendo una
integración distinta: no forman parte de este ensayo de bolsas públicas y no
se activarán sin sus claves de cuenta.
