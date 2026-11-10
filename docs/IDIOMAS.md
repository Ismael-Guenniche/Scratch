# Idiomas

- **Inglés** (por defecto) y **español**. Lo primero que pregunta la bienvenida es el idioma; se cambia luego en *Ajustes → IDIOMA*.
- Los textos del código se escriben en español y pasan por `Tr.t("...")`. La tabla español → inglés está en `TrEn.java`; si un texto no está en la tabla se muestra tal cual.
- La política de privacidad tiene versión en inglés y en español (`Privacy.sections()`).
- **Japonés experimental (solo sabor `personal`)**: kana sin kanji y el inglés debajo. Está en `app/src/personal/.../TrJa.java`; el sabor `play` incluye un `TrJa` vacío (`ENABLED = false`), por eso no aparece la opción ni el texto en Google Play. Las vistas de puntos (`DotTextView`) no tienen kana y muestran solo el inglés; los botones, ajustes, diálogos y avisos sí muestran kana. Los textos sin entrada japonesa salen solo en inglés.
- Añadir un texto: escribirlo en español con `Tr.t(...)` y añadir su par en `TrEn.java` (y, si se quiere, el kana en `TrJa.java` usando el texto en inglés como clave).
