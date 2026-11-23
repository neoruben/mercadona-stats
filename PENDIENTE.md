# PENDIENTE — Estado del trabajo y siguientes pasos

Notas para continuar desde cualquier agente (Claude Code, Codex, Antigravity…). Las reglas generales del
proyecto están en [AGENTS.md](AGENTS.md). **Actualiza este archivo al terminar cada sesión.**

Última actualización: 23/11/2026.

---

## Dónde estamos

- **Rama:** `imap`. Todavía no hay PR a `main` (el usuario prefiere esperar).
- **Último commit subido:** `1936da2` «Sincronizar IMAP en segundo plano y documentar el proyecto para agentes». La CI pasa.
- **Versión:** `1.2.0` (`versionCode 6`) en `app/build.gradle.kts`.

### Hecho en la rama `imap`

1. **Importación por IMAP** con contraseña de aplicación (Gmail, iCloud, otros), ya en commits anteriores.
2. **Sincronización IMAP en segundo plano** (`sync/ImapSyncService`): servicio en primer plano con *wake lock* y
   notificación. Ya no se corta al apagar la pantalla; reconecta sola si se pierde la conexión.
   Probado en el móvil: 154 tickets importados con la pantalla apagada.
3. **Ayuda para crear la contraseña** de Gmail e iCloud en el formulario, y aviso de que Outlook/Hotmail no funcionan
   (Microsoft exige OAuth desde septiembre de 2024).
4. **Documentación para agentes:** `AGENTS.md`, más `CLAUDE.md`, `GEMINI.md` y `.agent/rules/` que lo importan.

### Hecho pero SIN COMMIT (en el árbol de trabajo)

- **Versión 1.2.0** en `app/build.gradle.kts`.
- **Firma de la release** en `app/build.gradle.kts`: lee `keystore.properties` de la ruta que indica
  `RELEASE_KEYSTORE_PROPERTIES` en `local.properties` (no versionado). Ver «Firma» más abajo.
- **README** con la sección «Novedades de la versión 1.2.0»: IMAP pasa a ser la vía recomendada y el inicio de
  sesión con Google queda como alternativa.
- **Histórico de precios por producto** (nuevo):
  - `stats/StatsCalculator.kt`: `PricePoint` y `ProductSpendStat.priceHistory` (un punto por ticket, ordenado por
    fecha; precio unitario o €/kg, o `total / cantidad` si falta).
  - `ui/ProductPriceDialog.kt`: diálogo con resumen (último, mínimo, máximo, variación), gráfica de línea con
    `Canvas` (se puede tocar cada punto) y lista de compras con ▲/▼ respecto a la anterior.
  - `ui/MercadonaAppUi.kt`: las tarjetas de la pestaña **Productos** se pueden pulsar, con una pista bajo el buscador.
  - Test nuevo en `StatsCalculatorTest` (`buildsPriceHistoryPerProductOrderedByDate`). Los tests pasan y las APK debug
    y release compilan.
- `AGENTS.md`, `docs/ARCHITECTURE.md` y `README.md` actualizados con todo lo anterior.
- `MercaStats-v.1.2.0.apk` en la raíz (ignorado por git): release firmada con la clave OneRate, **incluye** el
  histórico de precios.

---

## Siguientes pasos (en orden)

1. **Terminar de probar el histórico de precios en el móvil.**
   - Probado con KÉFIR (49 compras): el diálogo se abre, la gráfica y la lista son correctas y no hay errores.
   - Corregido después: el euro salía repetido (`1,10 € €/ud`) y la casilla «Último» se partía en dos líneas.
     Ahora se muestra `1,10 €/ud`. La corrección está instalada en el móvil, pero falta verla en pantalla
     (el móvil se volvió a bloquear).
   - Falta probar: tocar puntos de la gráfica, un producto con una sola compra y uno a peso (€/kg), como el plátano.
   - Si hace falta recompilar: `./gradlew :app:assembleDebug` y `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   - Tras la corrección, `MercaStats-v.1.2.0.apk` está desfasada: hay que regenerarla con `./gradlew :app:assembleRelease`.
2. **Commit** de los cambios pendientes (mensaje en español, en infinitivo) y **push** de `imap`, solo cuando el
   usuario lo pida. No incluir `gradle/`, `gradlew` ni `gradlew.bat` (ver punto 7).
3. **Decidir si IMAP pasa a ser el botón principal de la app.** El README ya lo presenta como la vía recomendada,
   pero en la pestaña **Tickets** el botón grande sigue siendo «Conectar Gmail», y IMAP es un enlace pequeño
   («¿Otro correo o una etiqueta concreta? Conectar por IMAP», en `ui/MercadonaAppUi.kt`). Si se cambia, actualizar
   el paso 2 de «Correo por IMAP» en el README, que cita ese texto.
4. **Antes de publicar la 1.2.0, resolver lo de la firma** (ver «Firma»): avisar en las notas de la versión y añadir
   la huella SHA-1 nueva en Google Cloud.
5. **PR de `imap` a `main`** cuando el usuario lo diga.
6. **Publicar la release** `v.1.2.0` en GitHub con `MercaStats-v.1.2.0.apk` (convención de nombre con punto tras la
   «v»). Las releases anteriores incluyen notas en español y en inglés.
7. ~~Wrapper de Gradle~~: hecho. Versionado con Gradle 9.9.2; la CI usa `./gradlew` con JDK 25 y pasa.

### Ideas para más adelante (no pedidas todavía)

- Soporte de Outlook/Hotmail con OAuth de Microsoft + XOAUTH2 en Jakarta Mail.
- Pasar la sincronización de Gmail y la importación de PDFs al mismo esquema de servicio en primer plano.

---

## Firma de la release (decisión tomada)

- El usuario ha decidido firmar con **la clave de la carpeta `C:\Users\Ruben\Documents\claves`** (`onerate-clave`,
  alias `onerate`, certificado `CN=OneRate`), que es la que usa para sus otras apps.
  - SHA-1: `05:2F:AC:CF:C5:D7:F2:3D:4D:F6:D8:BF:EE:DA:CF:19:5C:2D:21:26`
  - SHA-256: `08:0C:C3:81:C5:60:9F:AB:C2:A1:0D:77:39:24:CC:95:1C:66:E4:0D:C2:45:75:34:E6:EC:BA:B2:8E:77:F1:10`
- `local.properties` contiene `RELEASE_KEYSTORE_PROPERTIES=C:/Users/Ruben/Documents/claves/keystore.properties`.
  Dentro de ese archivo, `storeFile` está escrito con «Rubén» (con tilde), una ruta que no existe; por eso el build
  busca el keystore en la misma carpeta que el `.properties`. **No leas ni muestres las contraseñas.**
- **Importante:** las releases hasta la **1.1.3 se firmaron con la clave de depuración** de este PC
  (`C:\Users\Ruben\.android\debug.keystore`, `CN=Android Debug`, SHA-256 `C6:24:74:F8:…:01:41:18`). Por eso:
  - La 1.2.0 **no se instalará encima de la 1.1.3**: hay que desinstalar la anterior, lo que borra los tickets
    guardados. Hay que avisarlo en las notas de la release; los tickets se recuperan volviendo a sincronizar.
  - El **inicio de sesión con Google** de la release dejará de funcionar hasta que se añada la SHA-1 de OneRate
    (arriba) en el cliente OAuth Android de Google Cloud (paquete `io.github.neoruben.mercadonastats`). IMAP y PDF
    no se ven afectados.
- Comprobar la firma de una APK:
  `C:\Android\Sdk\build-tools\<versión>\apksigner.bat verify --print-certs <apk>`.

---

## Entorno y pruebas

- Móvil de pruebas conectado por USB (`adb devices`). La debug instalada es `io.github.neoruben.mercadonastats.debug`
  y tiene una cuenta IMAP configurada con unos 154 tickets. La release no está instalada en ese móvil.
- Si el móvil está bloqueado, pide al usuario que lo desbloquee: no introduzcas su PIN.
- En Git Bash, para los comandos `adb` con rutas del móvil (`/sdcard/…`), usa `MSYS_NO_PATHCONV=1`, o Git Bash
  convierte la ruta.
