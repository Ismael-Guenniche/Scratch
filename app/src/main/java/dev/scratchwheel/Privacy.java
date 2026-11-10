package dev.scratchwheel;

/** Política de privacidad de la app (misma que docs/PRIVACIDAD.md). Subir VERSION obliga a aceptarla de nuevo. */
final class Privacy {
    static final int VERSION = 1;
    static final String DATE = "10 de noviembre de 2026";

    private Privacy() {
    }

    static final String[][] SECTIONS = {
        {"QUÉ ES SCRATCH", "Scratch es un reproductor de música local para Android. Reproduce los archivos de audio que tú tienes en tu móvil. No tiene cuentas de usuario, ni servidores propios, ni anuncios, ni analítica."},
        {"QUÉ DATOS GUARDA", "Todo se guarda únicamente en tu dispositivo: la carpeta de música que eliges, la lista de canciones escaneadas, tus listas, favoritas, historial y estadísticas de escucha, las etiquetas de las canciones y tus ajustes. Nada de esto sale de tu móvil ni se envía a nadie."},
        {"PERMISOS QUE USA", "Música: Scratch no pide acceso a todo tu almacenamiento. Tú eliges una carpeta con el selector de archivos del sistema y la app solo lee esa carpeta.\nNotificaciones: para mostrar los controles de reproducción en la barra y en la pantalla de bloqueo.\nServicio en primer plano y mantener activo: para que la música siga sonando con la pantalla apagada.\nInternet: solo para la función opcional de Spotify y para abrir enlaces de búsqueda en tu navegador."},
        {"SPOTIFY (OPCIONAL)", "Si usas «Importar de Spotify», la app se conecta a Spotify con tu cuenta para leer únicamente tus listas (nombres de canciones y artistas) y emparejarlas con tus archivos locales. Las credenciales y tokens se guardan solo en tu móvil y puedes borrarlos cerrando la sesión desde la app o desinstalándola. Scratch no descarga audio de Spotify ni de ningún otro servicio. Spotify es un tercero con su propia política de privacidad."},
        {"COMPARTIR DATOS", "Scratch no vende, no comparte ni transmite tus datos a terceros."},
        {"BORRAR TUS DATOS", "Puedes borrar todo desde Ajustes del sistema → Aplicaciones → Scratch → Almacenamiento → Borrar datos, o desinstalando la app. Tus archivos de música no se tocan nunca."},
        {"MENORES", "Scratch no está dirigida a niños y no recopila datos de nadie."},
        {"CAMBIOS", "Si esta política cambia de forma importante, la app te pedirá aceptarla de nuevo."},
        {"CONTACTO", "Dudas o peticiones: https://github.com/Ismael-Guenniche/Scratch/issues"},
    };

    static final String DATE_EN = "November 10, 2026";

    static final String[][] SECTIONS_EN = {
        {"WHAT SCRATCH IS", "Scratch is a local music player for Android. It plays the audio files you have on your phone. It has no user accounts, no servers of its own, no ads and no analytics."},
        {"WHAT DATA IT KEEPS", "Everything is stored only on your device: the music folder you choose, the list of scanned songs, your playlists, favorites, listening history and statistics, song tags and your settings. None of this leaves your phone or is sent to anyone."},
        {"PERMISSIONS IT USES", "Music: Scratch does not ask for access to all your storage. You pick a folder with the system file picker and the app only reads that folder.\nNotifications: to show the playback controls in the status bar and on the lock screen.\nForeground service and keep-awake: so the music keeps playing with the screen off.\nInternet: only for the optional Spotify feature and to open search links in your browser."},
        {"SPOTIFY (OPTIONAL)", "If you use \"Import from Spotify\", the app connects to Spotify with your account to read only your playlists (song and artist names) and match them with your local files. Credentials and tokens are stored only on your phone and you can delete them by signing out in the app or uninstalling it. Scratch does not download audio from Spotify or any other service. Spotify is a third party with its own privacy policy."},
        {"SHARING DATA", "Scratch does not sell, share or transmit your data to third parties."},
        {"DELETING YOUR DATA", "You can delete everything from system Settings → Apps → Scratch → Storage → Clear data, or by uninstalling the app. Your music files are never touched."},
        {"CHILDREN", "Scratch is not aimed at children and does not collect data from anyone."},
        {"CHANGES", "If this policy changes in a significant way, the app will ask you to accept it again."},
        {"CONTACT", "Questions or requests: https://github.com/Ismael-Guenniche/Scratch/issues"},
    };

    static String[][] sections() {
        return Tr.ES.equals(Tr.lang) ? SECTIONS : SECTIONS_EN;
    }

    static String date() {
        return Tr.ES.equals(Tr.lang) ? DATE : DATE_EN;
    }

    static String text() {
        StringBuilder sb = new StringBuilder();
        for (String[] s : sections()) {
            sb.append(s[0]).append("\n").append(s[1]).append("\n\n");
        }
        return sb.toString().trim();
    }
}
