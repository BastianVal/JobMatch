#!/bin/sh
set -eu

env_file="${1:-/srv/jobmatch/.env}"
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

if [ ! -f "$env_file" ]; then
  echo "No existe el archivo de entorno: $env_file" >&2
  exit 2
fi

value_of() {
  sed -n "s/^$1=//p" "$env_file" | tail -n 1
}

for name in APP_HOST POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD JOBMATCH_BACKEND_IMAGE \
  JOBMATCH_FRONTEND_IMAGE JOBMATCH_IMAGE_TAG TOKEN_SECRET RATE_LIMIT_PEPPER SMTP_HOST \
  SMTP_USERNAME SMTP_PASSWORD; do
  value="$(value_of "$name")"
  if [ -z "$value" ] || printf '%s' "$value" | grep -q '^replace-with'; then
    echo "Define un valor real para $name en $env_file." >&2
    exit 2
  fi
done

case "$(value_of APP_HOST)" in
  *.example.com|example.com)
    echo 'APP_HOST debe ser el dominio real que apunta a esta instancia EC2.' >&2
    exit 2
    ;;
esac

case "$(value_of JOBMATCH_IMAGE_TAG)" in
  *' '*|latest|replace-*)
    echo 'JOBMATCH_IMAGE_TAG debe ser un tag inmutable, por ejemplo un SHA de Git.' >&2
    exit 2
    ;;
esac

cd "$root"
docker compose --env-file "$env_file" -f compose.yaml -f compose.prod.yaml config --quiet
printf 'Preflight de producción correcto: configuración y valores requeridos verificados.\n'
