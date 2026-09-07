# Fase 7G — Notificaciones temporales y cierre UX

Las notificaciones del espacio autenticado son una sola región visible. Cada mensaje
reemplaza el anterior, por lo que no se apilan ni duplican. Se cierran manualmente o
desaparecen a los cinco segundos; un mensaje nuevo reinicia el temporizador.

La región usa `role="status"`, `aria-live="polite"` y `aria-atomic="true"`. Al cambiar
entre **Para ti**, **Explorar**, **Seguimiento** y **Perfil**, el mensaje se descarta para
que no se filtre a otra sección.

La comprobación integral conserva el flujo existente de perfil, exploración y
seguimiento en `scripts/phase7-e2e.sh`.
