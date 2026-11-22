package dev.scratchwheel;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** La vista previa de Android Auto usa el MediaBrowser del sistema, que en el escritorio no hay. */
public class CarPreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Toast.makeText(this, Tr.ES.equals(Tr.lang)
                ? "La vista previa de Android Auto solo está en el móvil."
                : "The Android Auto preview is only available on the phone.",
                Toast.LENGTH_LONG).show();
        finish();
    }
}
