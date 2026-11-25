package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Ajustes con la misma estructura que Dot Words: Tema, Colores (colores del fondo + acento),
 * Tipografía (Dot / Mono / Clean) y Acerca de. Se añade Cristal para el estilo Lucent.
 */
public class SettingsActivity extends Activity {
    private Lucent th;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        prefs = Lucent.prefs(this);
        Player.I.init(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            redo();
        }
    }

    private ScrollView scroll;
    private LinearLayout wheelCard;
    private LinearLayout previewBox;
    private static int savedScroll = -1;

    /** Recrea la pantalla (para aplicar el tema) sin perder la posición del scroll ni parpadear. */
    private void redo() {
        savedScroll = scroll != null ? scroll.getScrollY() : -1;
        recreate();
        overridePendingTransition(0, 0);
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private void changed() {
        Lucent.stamp++;
        redo();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(6), 0, dp(10));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(th.ink);
        back.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(th.glass);
        bg.setStroke(dp(1), th.glassLine);
        back.setBackground(bg);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText(Tr.t("AJUSTES"));
        title.setPadding(dp(16), 0, 0, 0);
        header.addView(title);
        root.addView(header);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // TEMA
        LinearLayout c = plain(list, Tr.t("TEMA"));
        c.addView(segmented(new String[] {Tr.t("SISTEMA"), Tr.t("CLARO"), Tr.t("OSCURO")}, prefs.getInt("mode", 0),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("mode", v).apply();
                        changed();
                    }
                }));

        // COLORES
        // IDIOMA
        c = plain(list, Tr.t("IDIOMA"));
        final String[] langCodes = TrJa.ENABLED
                ? new String[] {Tr.EN, Tr.ES, Tr.JA} : new String[] {Tr.EN, Tr.ES};
        final String[] langNames = TrJa.ENABLED
                ? new String[] {"ENGLISH", "ESPAÑOL", "にほんご · EXPERIMENTAL"} : new String[] {"ENGLISH", "ESPAÑOL"};
        int langSel = 0;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(Tr.lang)) {
                langSel = i;
            }
        }
        c.addView(segmented(langNames, langSel, new Pick() {
            @Override
            public void on(int v) {
                Tr.set(SettingsActivity.this, langCodes[v]);
                redo();
            }
        }));

        c = card(list, Tr.t("COLORES"));
        boolean wallOk = Build.VERSION.SDK_INT >= 27;
        c.addView(switchRow(Tr.t("Colores del fondo"),
                wallOk ? Tr.t("Usa el color principal de tu fondo de pantalla como acento.")
                        : Tr.t("Requiere Android 8.1 o superior."),
                prefs.getBoolean("wall", false) && wallOk, wallOk, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wall", v).apply();
                        changed();
                    }
                }));
        c.addView(dots());
        TextView acc = body(Tr.t("Color de acento"));
        c.addView(acc);
        c.addView(swatches());

        // TIPOGRAFIA
        c = card(list, Tr.t("TIPOGRAFÍA"));
        c.addView(segmented(new String[] {"DOT", "MONO", "CLEAN"},
                prefs.getInt("font", Lucent.defaultLucent() ? 0 : 2), new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("font", v).apply();
                        changed();
                    }
                }));
        DotTextView prev = new DotTextView(this, 4f, th);
        prev.setText("AA 0123");
        prev.setPadding(0, dp(24), 0, dp(10));
        c.addView(prev);
        TextView sample = body("The quick brown fox jumps over the lazy dog.");
        sample.setPadding(0, dp(4), 0, dp(8));
        c.addView(sample);

        // RUEDA
        c = card(list, Tr.t("RUEDA"));
        wheelCard = c;
        previewBox = buildWheelPreview();
        LinearLayout.LayoutParams pbl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        pbl.topMargin = dp(6);
        pbl.bottomMargin = dp(10);
        c.addView(previewBox, pbl);
        c.addView(switchRow(Tr.t("Carátula del disco"),
                Tr.t("Muestra la imagen incrustada del archivo en vez del plato de puntos."),
                prefs.getBoolean("cover", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("cover", v).apply();
                    }
                }));
        c.addView(body(Tr.t("Aspecto del vinilo")));
        final int vsel = prefs.getInt("vskin", 0);
        c.addView(segmented(new String[] {Tr.t("CLÁSICO"), Tr.t("PUNTOS"), Tr.t("RAYAS"), Tr.t("ESPIRAL")},
                vsel < 4 ? vsel : -1, new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("vskin", v).apply();
                        redo();
                    }
                }));
        c.addView(segmented(new String[] {Tr.t("COLOR"), Tr.t("CRISTAL"), Tr.t("NEÓN")},
                vsel >= 4 ? vsel - 4 : -1, new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("vskin", v + 4).apply();
                        redo();
                    }
                }));
        c.addView(body(Tr.t("Estilo de la rueda (sin carátula)")));
        c.addView(segmented(new String[] {Tr.t("PUNTOS"), Tr.t("GRABADORA")}, prefs.getInt("wstyle", 0),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("wstyle", v).apply();
                        redo();
                    }
                }));
        TextView vnote = muted(Tr.t("Solo se ve con la carátula del disco activada."));
        vnote.setPadding(0, dp(8), 0, 0);
        c.addView(vnote);
        c.addView(dots());
        c.addView(switchRow(Tr.t("Mantener el tono"),
                Tr.t("Al mover la rueda rápido o lento el tono no sube ni baja (el audio se estira en vez de acelerarse). También aplica al control de velocidad."),
                prefs.getBoolean("keylock", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("keylock", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(body(Tr.t("Sensibilidad de la rueda")));
        c.addView(segmented(new String[] {Tr.t("LENTA"), "NORMAL", Tr.t("RÁPIDA")}, prefs.getInt("wsens", 1),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("wsens", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        TextView sensNote = muted(Tr.t("Cuánto audio recorre una vuelta: lenta = más preciso, rápida = más recorrido."));
        sensNote.setPadding(0, dp(8), 0, 0);
        c.addView(sensNote);
        c.addView(dots());
        c.addView(switchRow(Tr.t("Inercia al soltar"),
                Tr.t("Si sueltas la rueda lanzada con la música parada, sigue girando como un plato hasta frenarse."),
                prefs.getBoolean("inertia", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("inertia", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(body(Tr.t("Fricción")));
        c.addView(segmented(new String[] {Tr.t("ALTA"), Tr.t("MEDIA"), Tr.t("BAJA")}, prefs.getInt("wfric", 1),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("wfric", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Sonido de vinilo"),
                Tr.t("Añade el carácter de un disco real: una oscilación mínima del tono y un ruido de surco muy suave."),
                prefs.getBoolean("vinyl", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("vinyl", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Invertir el sentido"),
                Tr.t("Girar en sentido horario hace retroceder el audio, como en algunos platos."),
                prefs.getBoolean("winvert", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("winvert", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Vibración al girar"),
                Tr.t("Un toque suave cada pocos grados, como los pasos de un dial."),
                prefs.getBoolean("haptic", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("haptic", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Bloquear scratch"),
                Tr.t("La rueda solo gira; el centro sigue haciendo play y pausa."),
                prefs.getBoolean("wlock", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wlock", v).apply();
                    }
                }));

        // MODO DJ
        c = card(list, Tr.t("MODO DJ"));
        c.addView(muted(Tr.t("Elige qué funciones aparecen al activar el modo DJ.")));
        c.addView(switchRow("Hot cues",
                Tr.t("4 pads: toca para guardar un punto y vuelve a tocar para saltar. Pulsación larga lo borra."),
                prefs.getBoolean("dj_cues", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_cues", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Bucles y roll"),
                Tr.t("Bucle manual (IN / OUT / ½ / ×2) y roll de ¼, ½ y 1 s mientras mantienes."),
                prefs.getBoolean("dj_loop", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_loop", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow("Slip",
                "Botón SLIP: mientras haces scratch o bucle, la canción sigue por debajo y al soltar vuelve a donde estaría.",
                prefs.getBoolean("dj_slip", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_slip", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Saltos"),
                Tr.t("Botones para saltar ±4 y ±16 segundos."),
                prefs.getBoolean("dj_jump", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_jump", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Efectos"),
                Tr.t("Flanger, phaser, trans (gate), echo out y vinyl stop."),
                prefs.getBoolean("dj_fx", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_fx", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Cut y reverse"),
                Tr.t("Mantén CUT para silenciar a golpes y REVERSE para oír la canción hacia atrás."),
                prefs.getBoolean("dj_cut", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_cut", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("BPM y sincronía"),
                Tr.t("Detecta el tempo de cada canción, marca los pulsos y con SYNC iguala el tempo de las siguientes."),
                prefs.getBoolean("dj_bpm", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_bpm", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Vúmetro de puntos"),
                Tr.t("Medidor de nivel de puntos arriba del panel DJ."),
                prefs.getBoolean("dj_meter", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_meter", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Modo disco"),
                Tr.t("La rueda y el vúmetro cambian de color y pulsan con el volumen."),
                prefs.getBoolean("dj_disco", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_disco", v).apply();
                    }
                }));

        // BIG PICTURE
        c = card(list, Tr.t("BIG PICTURE"));
        c.addView(muted(Tr.t("Pantalla de módulos en una rejilla: abre el editor para añadir, quitar, mover y cambiar el tamaño de cada módulo.")));
        c.addView(actionRow(Tr.t("Editor de módulos"), Tr.t("PERSONALIZAR"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, BigEditorActivity.class));
            }
        }));
        c.addView(actionRow(Tr.t("Abrir Big Picture"), Tr.t("ABRIR"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, BigPictureActivity.class));
            }
        }));

        // SONIDO
        c = card(list, Tr.t("SONIDO"));
        c.addView(switchRow(Tr.t("Nivelar volumen (ReplayGain)"),
                Tr.t("Si el archivo trae ReplayGain, baja las canciones que suenan más fuerte para que todas se oigan parecido. Nunca sube el volumen."),
                prefs.getBoolean("replaygain", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("replaygain", v).apply();
                        Player.I.applyReplayGain(Player.I.currentUri());
                    }
                }));

        c.addView(body(Tr.t("Fundido entre canciones")));
        final int[] xf = {0, 2, 4, 6, 8, 10};
        int xs = 0;
        for (int i = 0; i < xf.length; i++) {
            if (xf[i] == prefs.getInt("crossfade", 0)) {
                xs = i;
            }
        }
        String[] xl = new String[xf.length];
        for (int i = 0; i < xf.length; i++) {
            xl[i] = xf[i] == 0 ? Tr.t("NO") : xf[i] + "S";
        }
        c.addView(segmented(xl, xs, new Pick() {
            @Override
            public void on(int v) {
                prefs.edit().putInt("crossfade", xf[v]).apply();
                Player.I.applyCrossfade();
                redo();
            }
        }));
        c.addView(muted(Tr.t("Las últimas canciones se mezclan con la siguiente cuando suena sola (necesita la precarga activada). Al saltar a mano no hay fundido.")));
        c.addView(dots());
        c.addView(body(Tr.t("Compensar la latencia")));
        c.addView(segmented(new String[] {Tr.t("NO"), Tr.t("AUTO")}, prefs.getInt("latcomp", 1) == 1 ? 1 : 0,
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("latcomp", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        c.addView(body(Tr.t("Ajuste fino")));
        c.addView(segmented(new String[] {"-40", "-20", "0", "+20", "+40"},
                Math.max(0, Math.min(4, prefs.getInt("lattrim", 2))), new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("lattrim", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        c.addView(muted(Tr.t("La rueda, el tiempo y la letra se muestran donde suena el audio de verdad, no donde va el motor. Si la rueda va por delante del sonido, sube el ajuste fino (en milisegundos); si va por detrás, bájalo.")
                + (AudioTune.measuredMs >= 0 ? " " + Tr.t("Latencia medida:") + " " + AudioTune.measuredMs + " ms." : "")));

        // BÚSQUEDA
        c = card(list, Tr.t("BÚSQUEDA"));
        c.addView(body(Tr.t("Tolerancia a errores")));
        c.addView(segmented(new String[] {Tr.t("NO"), Tr.t("POCA"), "NORMAL"},
                Math.max(0, Math.min(2, prefs.getInt("s_tol", SmartSearch.TOL_NORMAL))), new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("s_tol", v).apply();
                        redo();
                    }
                }));
        TextView tolNote = muted(Tr.t("NORMAL: «estepa» encuentra «Estopa» (un error en palabras de 4 a 6 letras, dos desde 7). POCA: solo un error y en palabras de 7 letras o más. NO: sin errores. Mayúsculas, acentos y guiones nunca cuentan: «one x» encuentra «One-X»."));
        tolNote.setPadding(0, dp(8), 0, 0);
        c.addView(tolNote);
        c.addView(dots());
        c.addView(body(Tr.t("Dónde buscar")));
        final String[] fLabels = {Tr.t("Título"), Tr.t("Artista"), Tr.t("Álbum"), Tr.t("Carpeta")};
        final String[] fDescs = {Tr.t("También el nombre del archivo."), "", "", ""};
        final int[] fBits = {SmartSearch.F_TITLE, SmartSearch.F_ARTIST, SmartSearch.F_ALBUM, SmartSearch.F_FOLDER};
        final int fields = prefs.getInt("s_fields", SmartSearch.F_ALL);
        for (int i = 0; i < fBits.length; i++) {
            final int bit = fBits[i];
            c.addView(switchRow(fLabels[i], fDescs[i], (fields & bit) != 0, true, new Toggle() {
                @Override
                public void on(boolean v) {
                    int f = prefs.getInt("s_fields", SmartSearch.F_ALL);
                    f = v ? (f | bit) : (f & ~bit);
                    if ((f & SmartSearch.F_ALL) == 0) {
                        // sin ningún campo no se podría buscar nada: se vuelve a todos
                        f = SmartSearch.F_ALL;
                        prefs.edit().putInt("s_fields", f).apply();
                        redo();
                        return;
                    }
                    prefs.edit().putInt("s_fields", f).apply();
                }
            }));
        }
        c.addView(dots());
        c.addView(switchRow(Tr.t("Dentro de las palabras"),
                Tr.t("Con 4 letras o más, también encuentra lo escrito en mitad de una palabra («park» en «Linkinpark»)."),
                prefs.getBoolean("s_inside", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("s_inside", v).apply();
                    }
                }));
        c.addView(switchRow(Tr.t("Ordenar por relevancia"),
                Tr.t("Al buscar, lo que más se parece sale arriba (pesa más el artista y el título). Si lo quitas, se mantiene el orden de la lista."),
                prefs.getBoolean("s_rel", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("s_rel", v).apply();
                    }
                }));

        // LISTAS CON CLAUDE (solo el sabor personal)
        if (Flavor.PERSONAL) {
            c = card(list, Tr.t("LISTAS CON CLAUDE"));
            c.addView(muted(Tr.t("Exporta tu biblioteca, mándasela a Claude con lo que quieras («solo Estopa», «canciones pesadas tipo Linkin Park»…) e importa el archivo que te devuelva: la lista se crea sola.")));
            c.addView(actionRow(Tr.t("Exportar la biblioteca"), Tr.t("EXPORTAR"), new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, "biblioteca-scratch.json");
                    startActivityForResult(i, REQ_LIB_EXPORT);
                }
            }));
            c.addView(actionRow(Tr.t("Importar una lista de Claude"), Tr.t("IMPORTAR"), new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(i, REQ_LIST_IMPORT);
                }
            }));
        }

        // ANDROID AUTO
        c = card(list, Tr.t("ANDROID AUTO"));
        c.addView(muted(Tr.t("Vista previa de lo que verás en el coche: usa el mismo servicio que Android Auto (explorador, búsqueda, cola y controles), con filas y botones grandes. Sirve también como modo coche.")));
        c.addView(actionRow(Tr.t("Probar sin coche"), Tr.t("ABRIR"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, CarPreviewActivity.class));
            }
        }));

        // PRECARGA
        c = card(list, Tr.t("PRECARGA"));
        c.addView(body(Tr.t("Canciones listas en memoria")));
        final int[] preVals = {0, 2, 3, 5, 8, 12};
        int preSel = 3;
        for (int i = 0; i < preVals.length; i++) {
            if (preVals[i] == prefs.getInt("precache", 5)) {
                preSel = i;
            }
        }
        String[] preLabels = new String[preVals.length];
        for (int i = 0; i < preVals.length; i++) {
            preLabels[i] = String.valueOf(preVals[i]);
        }
        c.addView(segmented(preLabels, preSel, new Pick() {
            @Override
            public void on(int v) {
                prefs.edit().putInt("precache", preVals[v]).apply();
                Player.I.precacheChanged();
                redo();
            }
        }));
        TextView preNote = muted(Tr.t("Las próximas canciones se cargan de una en una para que al cambiar no haya espera. Cada una ocupa unos 40 MB de memoria; si la app va justa, baja el número. Con 0 solo se prepara la siguiente."));
        preNote.setPadding(0, dp(8), 0, 0);
        c.addView(preNote);

        // ECUALIZADOR (5 bandas)
        c = card(list, Tr.t("ECUALIZADOR"));
        c.addView(switchRow(Tr.t("Ecualizador"),
                Tr.t("Ajusta graves, medios y agudos de todo lo que suena, sin cortes entre canciones."),
                prefs.getBoolean("eq_on", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("eq_on", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(eqPresets());
        final String[] bands = {"60 HZ", "230 HZ", "910 HZ", "3.6 KHZ", "14 KHZ"};
        for (int i = 0; i < 5; i++) {
            c.addView(eqBand(i, bands[i]));
        }

        // PERMISOS Y PRIVACIDAD
        c = card(list, Tr.t("PERMISOS Y PRIVACIDAD"));
        c.addView(muted(Tr.t("Scratch solo lee la carpeta de música que eliges (sin permiso de almacenamiento amplio) ")
                + Tr.t("y usa notificaciones para los controles de reproducción.")));
        c.addView(actionRow(Tr.t("Notificaciones"), Tr.t("ABRIR"), new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
        }));
        c.addView(dots());
        c.addView(actionRow(Tr.t("Política de privacidad"), Tr.t("LEER"), new Runnable() {
            @Override
            public void run() {
                new DotDialog(SettingsActivity.this, th).title(Tr.t("PRIVACIDAD"))
                        .message(Privacy.text() + Tr.t("\n\nVersión ") + Privacy.VERSION + " · " + Privacy.date())
                        .button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
            }
        }));

        // TEMPORIZADOR
        int left = Player.I.sleepMinutes();
        c = card(list, Tr.t("TEMPORIZADOR DE APAGADO"));
        c.addView(muted(left > 0 ? Tr.t("Se apagará en ") + left + Tr.t(" min.") : Tr.t("La música no se apagará sola.")));
        final int[] mins = {0, 15, 30, 60, 90};
        c.addView(segmented(new String[] {"NO", "15", "30", "60", "90"}, left == 0 ? 0 : -1,
                new Pick() {
                    @Override
                    public void on(int v) {
                        Player.I.setSleepMinutes(mins[v]);
                        redo();
                    }
                }));

        // CRISTAL
        c = card(list, Tr.t("CRISTAL"));
        c.addView(switchRow(Tr.t("Estilo Lucent"),
                Tr.t("Tarjetas de cristal sobre tu fondo. Apagado: tarjetas sólidas."),
                prefs.getBoolean("lucent", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("lucent", v).apply();
                        changed();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Rejilla de puntos"),
                Tr.t("Puntos tenues por toda la pantalla, sobre el fondo difuminado."),
                prefs.getBoolean("grid", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("grid", v).apply();
                        changed();
                    }
                }));
        if (Build.VERSION.SDK_INT >= 31 && th.lucent) {
            c.addView(dots());
            c.addView(body(Tr.t("Desenfoque del fondo")));
            c.addView(segmented(new String[] {"NO", Tr.t("SUAVE"), "NORMAL"}, prefs.getInt("blur", 2),
                    new Pick() {
                        @Override
                        public void on(int v) {
                            prefs.edit().putInt("blur", v).apply();
                            changed();
                        }
                    }));
        }

        // ICONO
        c = card(list, Tr.t("ICONO DE LA APP"));
        c.addView(muted(Tr.t("Elige el icono del lanzador. Puede tardar unos segundos en cambiar.")));
        iconCells = new ImageView[ICON_COUNT];
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, 0);
            for (int k = 0; k < 4; k++) {
                final int n = r * 4 + k + 1;
                ImageView iv = new ImageView(this);
                int id = getResources().getIdentifier("ic_icon" + n, "drawable", getPackageName());
                if (id != 0) {
                    iv.setImageResource(id);
                }
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(dp(4), dp(4), dp(4), dp(4));
                iv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setIcon(n);
                    }
                });
                iconCells[n - 1] = iv;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                lp.leftMargin = dp(3);
                lp.rightMargin = dp(3);
                row.addView(iv, lp);
            }
            c.addView(row);
        }
        refreshIcons();

        TextView about = new TextView(this);
        about.setText("SCRATCH 0.75");
        about.setTextSize(11);
        about.setTextColor(th.ink2);
        about.setTypeface(th.mono);
        about.setLetterSpacing(0.15f);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(18), 0, dp(10));
        list.addView(about);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        scroll = sv;
        if (savedScroll > 0) {
            final int y = savedScroll;
            savedScroll = -1;
            sv.post(new Runnable() {
                @Override
                public void run() {
                    scroll.scrollTo(0, y);
                }
            });
        }
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private static final int ICON_COUNT = 8;
    private ImageView[] iconCells;

    private void refreshIcons() {
        int sel = prefs.getInt("icon", 1);
        for (int i = 0; i < iconCells.length; i++) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setStroke(dp(2), i + 1 == sel ? th.accent : 0x00000000);
            iconCells[i].setBackground(g);
        }
    }

    /** Cambia el icono activando solo el alias de lanzador elegido. */
    private void setIcon(int n) {
        android.content.pm.PackageManager pm = getPackageManager();
        String pkg = getPackageName();
        pm.setComponentEnabledSetting(new android.content.ComponentName(pkg, "dev.scratchwheel.Icon" + n),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP);
        for (int i = 1; i <= ICON_COUNT; i++) {
            if (i != n) {
                pm.setComponentEnabledSetting(new android.content.ComponentName(pkg, "dev.scratchwheel.Icon" + i),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
            }
        }
        prefs.edit().putInt("icon", n).apply();
        refreshIcons();
    }

    private interface Pick {
        void on(int v);
    }

    private interface Toggle {
        void on(boolean v);
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(th.ink);
        t.setTypeface(th.regular);
        return t;
    }

    private TextView muted(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(th.ink2);
        t.setTypeface(th.regular);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private float previewAngle;
    private boolean previewTouching;
    private boolean previewPlaying = true;
    private JogWheelView previewWheel;
    private final Runnable previewSpin = new Runnable() {
        @Override
        public void run() {
            if (previewWheel == null) {
                return;
            }
            if (!previewTouching && previewPlaying) {
                previewAngle += 0.012f;
            }
            previewWheel.setAngle(previewAngle);
            previewWheel.postOnAnimation(this);
        }
    };

    private LinearLayout buildWheelPreview() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        previewWheel = new JogWheelView(this, th);
        previewWheel.setHaptic(prefs.getBoolean("haptic", true));
        previewWheel.setLocked(prefs.getBoolean("wlock", false));
        previewWheel.setCoverOn(prefs.getBoolean("cover", false));
        previewWheel.setVinylSkin(prefs.getInt("vskin", 0));
        previewWheel.setWheelStyle(prefs.getInt("wstyle", 0));
        previewWheel.setPlayingState(true);
        previewWheel.setCover(Covers.placeholder());
        final android.net.Uri cu = Player.I.currentUri();
        if (cu != null && prefs.getBoolean("cover", false)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final android.graphics.Bitmap b = Covers.embedded(getApplicationContext(), cu);
                    if (b != null && previewWheel != null) {
                        previewWheel.post(new Runnable() {
                            @Override
                            public void run() {
                                previewWheel.setCover(b);
                            }
                        });
                    }
                }
            }, "prevcover").start();
        }
        previewWheel.setListener(new JogWheelView.Listener() {
            @Override
            public void onTouchDown() {
                previewTouching = true;
            }

            @Override
            public void onRotate(double radians) {
                previewAngle += (float) radians;
            }

            @Override
            public void onTouchUp() {
                previewTouching = false;
            }

            @Override
            public void onHubTap() {
                previewPlaying = !previewPlaying;
                previewWheel.setPlayingState(previewPlaying);
            }
        });
        box.addView(previewWheel, new LinearLayout.LayoutParams(dp(190), dp(190)));
        return box;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (previewWheel != null) {
            previewWheel.postOnAnimation(previewSpin);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (previewWheel != null) {
            previewWheel.removeCallbacks(previewSpin);
        }
    }

    private LinearLayout card(LinearLayout parent, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(th.ink2);
        l.setTypeface(th.mono);
        l.setLetterSpacing(0.12f);
        l.setPadding(dp(8), dp(16), 0, dp(8));
        parent.addView(l);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(th.card(Lucent.R_L));
        c.setPadding(dp(22), dp(18), dp(22), dp(18));
        parent.addView(c, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c;
    }

    /** Grupo sin tarjeta (chips directamente sobre el fondo), como Idioma/Modo en Dot Calendar. */
    private LinearLayout plain(LinearLayout parent, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(th.ink2);
        l.setTypeface(th.mono);
        l.setLetterSpacing(0.12f);
        l.setPadding(dp(8), dp(16), 0, dp(2));
        parent.addView(l);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        parent.addView(c, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private View dots() {
        View v = new View(this);
        v.setBackground(new android.graphics.drawable.ColorDrawable(th.ghost));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        v.setLayoutParams(lp);
        return v;
    }

    private static final String[] EQ_NAMES = {"PLANO", "GRAVES+", "VOZ", "AGUDOS+", "ROCK", "ELECTRÓNICA"};
    private static final int[][] EQ_PRESETS = {
        {0, 0, 0, 0, 0}, {6, 4, 0, 0, 0}, {-2, 0, 4, 3, -1}, {0, 0, 0, 4, 6}, {5, 3, -2, 3, 5}, {6, 3, 0, 2, 5}};

    private View eqPresets() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(6));
        int sel = prefs.getInt("eq_preset", 0);
        for (int i = 0; i < EQ_NAMES.length; i++) {
            final int k = i;
            TextView b = th.chip(this, Tr.t(EQ_NAMES[i]), i == sel, 14);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor e = prefs.edit().putInt("eq_preset", k).putBoolean("eq_on", true);
                    for (int j = 0; j < 5; j++) {
                        e.putInt("eq" + j, EQ_PRESETS[k][j]);
                    }
                    e.apply();
                    Player.I.applyWheelPrefs();
                    redo();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(b, lp);
        }
        hs.addView(row);
        return hs;
    }

    private View eqBand(final int band, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(11);
        name.setTextColor(th.ink2);
        name.setTypeface(th.mono);
        row.addView(name, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));
        final TextView val = new TextView(this);
        val.setTextSize(12);
        val.setTextColor(th.ink);
        val.setTypeface(th.mono);
        val.setGravity(Gravity.END);
        int db = prefs.getInt("eq" + band, 0);
        val.setText(eqText(db));
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(24);
        sb.setProgress(db + 12);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                if (!user) {
                    return;
                }
                int d = p - 12;
                val.setText(eqText(d));
                prefs.edit().putInt("eq" + band, d).putInt("eq_preset", -1).putBoolean("eq_on", true).apply();
                Player.I.applyWheelPrefs();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
            }
        });
        row.addView(sb, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(val, new LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static String eqText(int db) {
        return (db > 0 ? "+" : "") + db + " dB";
    }

    private View segmented(String[] opts, int selected, final Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        for (int i = 0; i < opts.length; i++) {
            final int value = i;
            TextView b = th.chip(this, opts[i], i == selected, opts.length > 4 ? 12 : 18);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.on(value);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(b, lp);
        }
        return row;
    }

    private View actionRow(String label, String action, final Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        row.addView(body(label), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = th.chip(this, action, false, 18);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        row.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View switchRow(String label, String desc, boolean on, boolean enabled,
            final Toggle t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(body(label));
        TextView d = muted(desc);
        d.setPadding(0, dp(2), 0, 0);
        txt.addView(d);
        row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setEnabled(enabled);
        sw.setThumbTintList(ColorStateList.valueOf(on ? th.accent : th.dotOff));
        sw.setTrackTintList(ColorStateList.valueOf(Lucent.withAlpha(on ? th.accent : th.ink, 0.35f)));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                t.on(checked);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        row.addView(sw, lp);
        return row;
    }

    private View swatches() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(4));
        int sel = prefs.getInt("accent", 0);
        boolean wall = prefs.getBoolean("wall", false) && Build.VERSION.SDK_INT >= 27;
        for (int i = 0; i < Lucent.ACCENTS.length; i++) {
            final int value = i;
            int col = Lucent.ACCENTS[i] == 0 ? th.ink : Lucent.ACCENTS[i];
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            if (i == sel && !wall) {
                g.setStroke(dp(3), th.ink);
            } else {
                g.setStroke(dp(1), th.glassLine);
            }
            v.setBackground(g);
            v.setAlpha(wall ? 0.4f : 1f);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    prefs.edit().putInt("accent", value).putBoolean("wall", false).apply();
                    changed();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(10);
            row.addView(v, lp);
        }
        hs.addView(row);
        return hs;
    }

    // ------------------------------------------------------- listas con Claude

    private static final int REQ_LIB_EXPORT = 41;
    private static final int REQ_LIST_IMPORT = 42;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) {
            return;
        }
        if (req == REQ_LIB_EXPORT && data.getData() != null) {
            exportLibrary(data.getData());
        } else if (req == REQ_LIST_IMPORT) {
            java.util.ArrayList<android.net.Uri> us = new java.util.ArrayList<android.net.Uri>();
            if (data.getClipData() != null) {
                for (int k = 0; k < data.getClipData().getItemCount(); k++) {
                    us.add(data.getClipData().getItemAt(k).getUri());
                }
            } else if (data.getData() != null) {
                us.add(data.getData());
            }
            importLists(us);
        }
    }

    private static java.util.List<PlaylistJson.Song> librarySongs() {
        java.util.ArrayList<PlaylistJson.Song> l = new java.util.ArrayList<PlaylistJson.Song>();
        for (Player.Track t : new java.util.ArrayList<Player.Track>(Player.I.tracks)) {
            l.add(new PlaylistJson.Song(t.title, t.artist, t.album, t.durMs, t.name, t.folder()));
        }
        return l;
    }

    private void exportLibrary(final android.net.Uri dest) {
        if (Player.I.count() == 0) {
            android.widget.Toast.makeText(this, Tr.t("La biblioteca está vacía"), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    java.util.List<PlaylistJson.Song> songs = librarySongs();
                    String v = "?";
                    try {
                        v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception ignored) {
                    }
                    String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                            .format(new java.util.Date());
                    java.io.OutputStream o = getContentResolver().openOutputStream(dest, "wt");
                    o.write(PlaylistJson.exportLibrary(songs, v, date).getBytes("UTF-8"));
                    o.close();
                    msg = Tr.t("Biblioteca exportada: ") + songs.size() + Tr.t(" CANCIONES");
                } catch (Exception e) {
                    msg = Tr.t("No se pudo exportar la biblioteca");
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        android.widget.Toast.makeText(app, m, android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private String readText(android.net.Uri u) throws java.io.IOException {
        java.io.InputStream in = getContentResolver().openInputStream(u);
        if (in == null) {
            throw new java.io.IOException("null");
        }
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > 8 * 1024 * 1024) {
                    throw new java.io.IOException("big");
                }
            }
            return new String(b.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private void importLists(final java.util.List<android.net.Uri> us) {
        if (us.isEmpty()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder report = new StringBuilder();
                java.util.List<PlaylistJson.Song> lib = librarySongs();
                java.util.List<Player.Track> tr = new java.util.ArrayList<Player.Track>(Player.I.tracks);
                int made = 0;
                for (android.net.Uri u : us) {
                    java.util.List<PlaylistJson.Playlist> lists;
                    try {
                        lists = PlaylistJson.parse(readText(u));
                    } catch (IllegalArgumentException e) {
                        report.append("library".equals(e.getMessage())
                                ? Tr.t("Ese archivo es la biblioteca exportada, no una lista de Claude.")
                                : Tr.t("El archivo no es un JSON válido.")).append("\n\n");
                        continue;
                    } catch (Exception e) {
                        report.append(Tr.t("No se pudo leer el archivo.")).append("\n\n");
                        continue;
                    }
                    for (PlaylistJson.Playlist pl : lists) {
                        PlaylistJson.Match m = PlaylistJson.match(pl, lib);
                        if (m.found.isEmpty()) {
                            report.append("«").append(pl.name).append("»: ")
                                    .append(Tr.t("ninguna canción encontrada")).append("\n\n");
                            continue;
                        }
                        String name = uniqueName(pl.name);
                        java.util.ArrayList<String> uris = new java.util.ArrayList<String>();
                        for (int idx : m.found) {
                            uris.add(tr.get(idx).uri.toString());
                        }
                        Playlists.I.create(name);
                        Playlists.I.add(name, uris);
                        made++;
                        report.append("«").append(name).append("»: ").append(m.found.size())
                                .append(Tr.t(" CANCIONES"));
                        if (!m.missing.isEmpty()) {
                            report.append(" · ").append(m.missing.size()).append(Tr.t(" no encontradas"));
                            for (int k = 0; k < Math.min(5, m.missing.size()); k++) {
                                report.append("\n– ").append(m.missing.get(k));
                            }
                            if (m.missing.size() > 5) {
                                report.append("\n…");
                            }
                        }
                        report.append("\n\n");
                    }
                }
                final int fm = made;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        new DotDialog(SettingsActivity.this, th)
                                .title(fm > 0 ? Tr.t("LISTA CREADA") : Tr.t("NO SE CREÓ NINGUNA LISTA"))
                                .message(report.toString().trim())
                                .button("OK", Lucent.ACCENT, null).show();
                    }
                });
            }
        }).start();
    }

    /** Nombre libre para la lista: si ya existe, «Nombre (2)», «Nombre (3)»… (máx. 40 letras). */
    private static String uniqueName(String base) {
        String b = base.replace('\n', ' ').replace('\t', ' ').trim();
        if (b.length() == 0) {
            b = "Claude";
        }
        if (b.length() > 40) {
            b = b.substring(0, 40).trim();
        }
        if (Playlists.I.find(b) == null) {
            return b;
        }
        for (int n = 2; n < 1000; n++) {
            String suf = " (" + n + ")";
            String c = (b.length() + suf.length() > 40 ? b.substring(0, 40 - suf.length()).trim() : b) + suf;
            if (Playlists.I.find(c) == null) {
                return c;
            }
        }
        return b;
    }
}
