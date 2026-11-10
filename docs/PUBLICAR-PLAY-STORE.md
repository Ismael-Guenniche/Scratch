# Publicar Scratch en Google Play

> Scratch es una app **Android**, así que su «App Store» es **Google Play**. La App Store de Apple no es posible: haría falta reescribir la app en Swift.

Datos de requisitos comprobados en la documentación de Google (nov. 2026). Revísalos antes de empezar: cambian cada año.

## 0. Antes de nada: lo que bloquea la publicación

Estado en la **0.33**: ya está migrada a `targetSdk 36`, con tipo de servicio, permiso de notificaciones y bienvenida con política de privacidad. Falta lo marcado «pendiente»:

| Bloqueo | Estado actual | Qué hay que hacer |
|---|---|---|
| **Target API** | hecho: `targetSdk 36` (probar en móvil) | Play exige **API 36 (Android 16)** para apps nuevas y actualizaciones desde el 31-08-2026. |
| Servicio en primer plano | hecho: `mediaPlayback` | Declarar `android:foregroundServiceType="mediaPlayback"` y el permiso `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (obligatorio desde API 34). |
| Notificaciones | hecho: se pide en la bienvenida | Pedir `POST_NOTIFICATIONS` en tiempo de ejecución (API 33+) o la notificación de reproducción no aparecerá. |
| Pantalla completa | pendiente de comprobar en móvil | A partir de API 35 las apps se dibujan *edge-to-edge*; revisar márgenes de todas las pantallas. |
| Formato de entrega | pendiente: solo APK | Play exige **AAB** (Android App Bundle): `./gradlew bundlePlayRelease` (siempre el sabor **play**: el japonés experimental no debe subirse). |
| Política de privacidad | hecha en la app y en `docs/PRIVACIDAD.md`; falta publicarla en una URL y añadir un correo de contacto | Obligatoria (la app declara `INTERNET`). Ver §5. |

Cambiar `targetSdk` de 28 a 36 afecta a permisos, servicios, orientación y almacenamiento: **es una tarea de código** (no solo cambiar un número) y hay que volver a probar en el móvil. Pídeme que la haga cuando quieras.

## 1. Cuenta de desarrollador

1. Entra en <https://play.google.com/console> con una cuenta de Google.
2. Elige tipo de cuenta:
   - **Personal:** para ti, sin empresa. Pago único de registro (25 USD) y verificación de identidad.
   - **Organización:** para empresa; exige número D-U-N-S.
3. **Importante para cuentas personales creadas después del 13-nov-2023:** antes de pasar a producción hay que hacer una **prueba cerrada con al menos 12 testers durante 14 días seguidos** y luego pedir acceso a producción. Las cuentas de organización están exentas.

Cuenta con unas **3 semanas** hasta ver la app pública la primera vez.

## 2. Preparar la firma

Google gestiona la clave final (*Play App Signing*); tú firmas con una **clave de subida**.

```bash
keytool -genkeypair -v -keystore scratch-upload.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000
```

- Guarda el `.jks` y las contraseñas en **dos sitios** fuera del repositorio (gestor de contraseñas + copia). Si pierdes la clave de subida se puede restablecer con Google, pero es un trámite.
- `.gitignore` ya excluye `*.jks`, `*.keystore`, `keystore.properties` y `*.aab`.

Crea `keystore.properties` en la raíz (no se sube a git):

```
storeFile=scratch-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Y en `app/build.gradle` añade una configuración de firma `release` que lea ese archivo, y `buildTypes { release { signingConfig signingConfigs.release; minifyEnabled true; shrinkResources true } }`. (Si activas `minifyEnabled`, prueba la build release entera: la app usa reflexión en pocos sitios, pero conviene comprobarlo.)

## 3. Generar el AAB

1. Sube `versionCode` y `versionName` en `app/build.gradle` (cada subida a Play necesita un `versionCode` mayor).
2. `./gradlew bundleRelease`
3. Resultado: `app/build/outputs/bundle/playRelease/app-play-release.aab`.
4. Instala una build release en tu móvil y pruébala (reproducir, rueda, notificación, Android Auto, rotación).

## 4. Crear la app en Play Console

1. **Crear app** → nombre, idioma por defecto (español), *Aplicación*, *Gratuita*, acepta las declaraciones.
2. **Panel → Configurar tu app** y completa en orden:
   - **Acceso a la app:** sin login; todo el contenido accesible.
   - **Anuncios:** no contiene anuncios.
   - **Clasificación de contenido:** cuestionario IARC (reproductor de música local → apta para todos).
   - **Público objetivo:** mayores de 13 o «todos»; **no** dirigida a niños.
   - **Seguridad de los datos (Data safety):** ver §5.
   - **Política de privacidad:** URL pública.
   - **Categoría:** *Música y audio*. Datos de contacto: tu correo.
3. **Ficha de la tienda (principal):**
   - Nombre (máx. 30), descripción breve (80), descripción completa (4000).
   - Icono 512×512 PNG, gráfico de funciones 1024×500.
   - Capturas de teléfono (mín. 2, recomendado 4–8): inicio, rueda, modo DJ, estadísticas, Wrapped, ajustes. Hechas en tu Nothing Phone.
4. **Pruebas → Prueba interna** (hasta 100 testers, sin revisión larga): sube el AAB aquí primero para comprobar que Play lo acepta.

## 5. Privacidad y Data safety

Scratch no tiene servidores propios ni analítica. Aun así debes declararlo:

- **Datos recopilados por la app:** ninguno que salga del móvil. Biblioteca, listas, historial y estadísticas se guardan **solo en el dispositivo**.
- **Función de Spotify (opcional):** si el usuario usa «Sincronizar Spotify», su cliente habla con Spotify (OAuth PKCE, solo lectura de listas). Menciónalo en la política: el token se guarda en el dispositivo y no se envía a nadie más.
- **Permisos:** `INTERNET` (Spotify), `FOREGROUND_SERVICE` + tipo `mediaPlayback`, `WAKE_LOCK`, `POST_NOTIFICATIONS`. El acceso a la música es por el selector de carpetas del sistema (SAF), sin permisos de almacenamiento amplios.
- **Política de privacidad:** una página pública (GitHub Pages sirve). Debe decir: qué datos se guardan y dónde, que no se comparten, uso de Spotify, cómo borrar los datos (borrar caché/desinstalar) y un correo de contacto.

## 6. Cosas de política que mirar

- **Nombre y marcas:** «Scratch» es también el nombre del lenguaje del MIT; comprueba que no choque en la tienda. No uses logos ni nombres de Teenage Engineering, Nothing o Spotify como si fueran tuyos; «inspirado en» en la descripción está bien, sin usar sus marcas en el nombre ni en el icono.
- **Spotify:** respeta su política de desarrollador (no descargar audio, no usar su contenido para entrenar modelos). La app ya no descarga nada.
- **Contenido del usuario:** la app solo reproduce archivos del propio usuario.
- **Enlaces a Bandcamp/Qobuz/YouTube Music** son enlaces de búsqueda en el navegador, no descargas.
- **Sonidos del sampler:** están sintetizados en la app, sin derechos de terceros.
- **Fuentes:** Geist se distribuye bajo OFL; incluye la licencia en la pantalla «Acerca de» o en la ficha.

## 7. Ruta de publicación (cuenta personal nueva)

1. **Prueba interna:** subes el AAB, pruebas tú.
2. **Prueba cerrada:** creas la lista de 12+ testers (correos de Google o grupo de Google), publicas la build y compartes el enlace de opt-in. **14 días seguidos** con ≥ 12 inscritos.
3. **Solicitar acceso a producción** en Play Console (te preguntan cómo fue la prueba y si la app está lista).
4. Revisión de Google (normalmente unos días).
5. **Producción:** *Crear versión nueva* → subes el AAB → notas de la versión → *Revisar* → *Iniciar lanzamiento*. Puedes empezar con un despliegue gradual (p. ej. 20 %).

## 8. Después de publicar

- Cada actualización: subir `versionCode`, generar AAB, subirlo (primero a interna), notas de versión.
- **Cada año** (en torno al 31 de agosto) hay que subir de nuevo el `targetSdk` al nivel que exija Google.
- Mira *Android vitals* y *Informes de fallos* en la consola; la app no tiene crash reporting propio.

## Lista de comprobación

- [ ] `targetSdk 36`, `compileSdk 36`, pruebas en móvil
- [ ] `foregroundServiceType` + permisos de servicio
- [ ] Permiso de notificaciones en ejecución
- [ ] Firma release y `keystore.properties` fuera de git
- [ ] AAB generado y probado
- [ ] Política de privacidad publicada
- [ ] Capturas, icono 512, gráfico 1024×500, descripciones
- [ ] Cuestionarios: contenido, Data safety, anuncios, público
- [ ] Prueba cerrada: 12 testers × 14 días (cuenta personal)
- [ ] Solicitud de producción enviada
