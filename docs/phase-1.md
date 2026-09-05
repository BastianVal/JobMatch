# Fase 1 — Identidad y acceso

## Alcance entregado

- Registro con correo normalizado y contraseña Argon2id.
- Verificación de correo y recuperación de contraseña mediante tokens de un solo uso, expirables y almacenados únicamente como SHA-256.
- Correos entregados por la cola durable; Mailpit funciona como SMTP local privado.
- Sesiones opacas persistidas en PostgreSQL, inactividad de 30 minutos y vida absoluta de 7 días.
- Cookie `HttpOnly`, `SameSite=Lax` y `Secure` en producción; protección CSRF sincronizada.
- Login con respuesta uniforme, límites por hash de correo e IP y renovación del identificador de sesión.
- Consulta y eliminación de la cuenta autenticada sin aceptar identificadores de propietario del cliente.
- Al restablecer contraseña o eliminar una cuenta se revocan todas sus sesiones.

## Flujo HTTP local

Todos los `POST` y `DELETE` requieren primero obtener una cookie y un token CSRF:

```bash
curl -c cookies.txt http://localhost:8090/api/v1/auth/csrf
```

Usa el valor `token` de la respuesta en `X-CSRF-TOKEN`, conservando `cookies.txt` con `-b cookies.txt`:

```bash
curl -b cookies.txt -X POST http://localhost:8090/api/v1/auth/register \
  -H 'Content-Type: application/json' -H 'X-CSRF-TOKEN: <token>' \
  -d '{"email":"persona@example.com","password":"una contraseña segura"}'
```

El worker enviará el mensaje a Mailpit. El enlace verifica la cuenta con `POST /api/v1/auth/verify-email`; el mismo patrón aplica a `password/forgot` y `password/reset`. Después de verificar, `POST /api/v1/auth/login` crea la sesión y `GET /api/v1/me/account` devuelve únicamente su propietario.

## Evidencia automatizada

- `TokenCodecTest`: firma por propósito, manipulación y hash no reversible.
- `IdentityServiceTest`: normalización, ausencia de contraseña en tareas y eliminación aislada por propietario.
- `JdbcIdentityAdapterIT`: aislamiento de mutaciones entre cuentas en PostgreSQL real.
- Las reglas de arquitectura y pruebas de la plataforma continúan activas.
- `scripts/phase1-e2e.sh`: CSRF, correo, token de un uso, session fixation, recuperación, revocación y propiedad contra el stack completo.
