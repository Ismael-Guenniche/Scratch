package dev.scratchwheel;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * El inicio de sesión con el reproductor web necesita un navegador incrustado, que en el escritorio no hay.
 * Quedan las otras dos vías: CSV de Exportify y la API de Spotify con tu Client ID.
 */
public class SpotifyWebActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Toast.makeText(this, Tr.ES.equals(Tr.lang)
                ? "La sesión web no está disponible en PC. Usa un CSV de Exportify o la API con tu Client ID."
                : "Web session is not available on PC. Use an Exportify CSV or the API with your Client ID.",
                Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }
}
