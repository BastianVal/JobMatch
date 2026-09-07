# Fase 5 — Importación de CV retirada

## Decisión vigente

La importación automática de CV no forma parte de esta versión. El usuario captura y
mantiene manualmente en **Perfil** su trayectoria, educación, certificaciones, idiomas
y habilidades. La aplicación no ofrece endpoints de carga, extracción, revisión,
descarga ni eliminación de CV.

Esta reducción elimina Apache Tika, Docling, Ollama, el modelo semántico, el volumen de
archivos y el procesamiento `EXTRACT_CV` del despliegue. Por tanto, levantar JobMatch no
descarga imágenes ni modelos asociados con el análisis de documentos.

## Compatibilidad de base de datos

`V8__cv_import.sql` se conserva sin cambios porque Flyway exige que una migración ya
publicada mantenga su checksum. La migración posterior de retiro marca como fallidas
las tareas y ejecuciones antiguas que hayan quedado pendientes, pero no borra archivos
ni datos históricos de usuarios existentes de forma automática.

En una instalación que sí haya recibido CV antes de esta retirada, el administrador
puede respaldar o eliminar manualmente el antiguo almacenamiento cuando haya confirmado
que ya no lo necesita.
