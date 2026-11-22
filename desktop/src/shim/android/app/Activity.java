package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

public class Activity extends Context implements Window.Callback {
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;

    private Window window;
    private desk.Pane pane;
    private desk.Layer layer;
    private Intent intent;
    private Activity caller;
    private int requestCode = -1;
    private int resultCode = RESULT_CANCELED;
    private Intent resultData;
    private boolean finishing;
    private boolean destroyed;
    private boolean resumed;
    private float scale = 1f;
    private int maxWidthDp;
    private boolean everResumed;
    private boolean started;
    private final Resources resources = new Resources();
    private android.window.OnBackInvokedCallback backCallback;

    // ------------------------------------------------------------ lo que usa el Shell

    public void attach(desk.Pane p, Intent i, Activity from, int req) {
        pane = p;
        intent = i;
        caller = from;
        requestCode = req;
        scale = p.scale;
        maxWidthDp = desk.Shell.maxWidthDp(getClass());
        window = new Window(this);
        window.setCallback(this);
        final View decor = window.getDecorView();
        layer = new desk.Layer(decor, desk.Layer.ACTIVITY) {
            @Override
            public void place(int paneW, int paneH) {
                // Pantallas pensadas para móvil a ventana completa: columna centrada en vez de estirarse.
                int side = contentSide(paneW);
                if (decor.getPaddingLeft() != side) {
                    decor.setPadding(side, 0, side, 0);
                }
                super.place(paneW, paneH);
            }
        };
        layer.owner = this;
        window.layer = layer;
        syncMetrics();
    }

    private int contentSide(int paneW) {
        if (maxWidthDp <= 0) {
            return 0;
        }
        int max = Math.round(maxWidthDp * desk.Rt.density * scale);
        return Math.max(0, (paneW - max) / 2);
    }

    /** Escala del panel con la que se creó esta pantalla. */
    public float scaleUsed() {
        return scale;
    }

    public desk.Pane pane() {
        return pane;
    }

    public desk.Layer layer() {
        return layer;
    }

    public Activity callerActivity() {
        return caller;
    }

    public int requestCode() {
        return requestCode;
    }

    public int resultCode() {
        return resultCode;
    }

    public Intent resultData() {
        return resultData;
    }

    /** Copia el tamaño del panel a los recursos. Devuelve true si cambió la orientación. */
    public boolean syncMetrics() {
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration cfg = resources.getConfiguration();
        int w = pane == null ? 0 : pane.getWidth();
        int h = pane == null ? 0 : pane.getHeight();
        if (w <= 0 || h <= 0) {
            java.awt.Dimension d = pane == null ? null : pane.getPreferredSize();
            w = d == null || d.width <= 0 ? 420 : d.width;
            h = d == null || d.height <= 0 ? 800 : d.height;
        }
        w -= 2 * contentSide(w);
        float den = desk.Rt.density * scale;
        dm.density = den;
        dm.scaledDensity = den;
        dm.densityDpi = Math.round(160 * den);
        dm.xdpi = dm.densityDpi;
        dm.ydpi = dm.densityDpi;
        dm.widthPixels = w;
        dm.heightPixels = h;
        int old = cfg.orientation;
        boolean land = w > h || (pane != null && pane.wide && w >= 600 * den);
        cfg.orientation = land ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        cfg.screenWidthDp = Math.round(w / den);
        cfg.screenHeightDp = Math.round(h / den);
        cfg.smallestScreenWidthDp = Math.min(cfg.screenWidthDp, cfg.screenHeightDp);
        cfg.densityDpi = dm.densityDpi;
        cfg.uiMode = Configuration.UI_MODE_TYPE_NORMAL
                | (desk.Sys.isDark() ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return old != cfg.orientation;
    }

    public void performCreate() {
        onCreate(null);
        if (pane != null) {
            pane.base = window.baseColor(pane.base);
        }
    }

    public void performStart() {
        if (!started) {
            started = true;
            onStart();
        }
    }

    public void performResume() {
        if (!resumed && !destroyed) {
            resumed = true;
            everResumed = true;
            onResume();
            onPostResume();
        }
    }

    public void performPause() {
        if (resumed) {
            resumed = false;
            onPause();
        }
    }

    public void performStop() {
        if (started) {
            started = false;
            onStop();
        }
    }

    public void performRestart() {
        onRestart();
    }

    public void performDestroy() {
        if (!destroyed) {
            destroyed = true;
            onDestroy();
        }
    }

    public void performConfigurationChanged() {
        onConfigurationChanged(resources.getConfiguration());
    }

    public void performBack() {
        if (backCallback != null) {
            backCallback.onBackInvoked();
        } else {
            onBackPressed();
        }
    }

    public boolean everResumed() {
        return everResumed;
    }

    public boolean isResumedNow() {
        return resumed;
    }

    public void markFinishing() {
        finishing = true;
    }

    // ------------------------------------------------------------ API de Android

    @Override
    public Resources getResources() {
        return resources;
    }

    public Application getApplication() {
        return desk.Shell.application();
    }

    public Window getWindow() {
        return window;
    }

    public android.view.WindowManager getWindowManager() {
        return new android.view.WindowManager(this);
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent i) {
        intent = i;
    }

    public void setContentView(View v) {
        window.setContentView(v);
    }

    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        window.setContentView(v, lp);
    }

    public void addContentView(View v, ViewGroup.LayoutParams lp) {
        window.contentParent().addView(v, lp);
    }

    public <T extends View> T findViewById(int id) {
        return window.findViewById(id);
    }

    public View getCurrentFocus() {
        return window.getCurrentFocus();
    }

    protected void onCreate(Bundle saved) {
    }

    protected void onStart() {
    }

    protected void onRestart() {
    }

    protected void onResume() {
    }

    protected void onPostResume() {
    }

    protected void onPause() {
    }

    protected void onStop() {
    }

    protected void onDestroy() {
    }

    protected void onNewIntent(Intent i) {
    }

    public void deliverNewIntent(Intent i) {
        onNewIntent(i);
    }

    protected void onSaveInstanceState(Bundle out) {
    }

    protected void onRestoreInstanceState(Bundle in) {
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    public void deliverResult(int req, int res, Intent data) {
        if (!destroyed) {
            onActivityResult(req, res, data);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    }

    public void onConfigurationChanged(Configuration c) {
    }

    public void onWindowFocusChanged(boolean has) {
    }

    public void onUserInteraction() {
    }

    public void onLowMemory() {
    }

    public void onTrimMemory(int level) {
    }

    public void onBackPressed() {
        finish();
    }

    public boolean onKeyDown(int code, KeyEvent e) {
        return false;
    }

    public boolean onKeyUp(int code, KeyEvent e) {
        return false;
    }

    public boolean dispatchKeyEvent(KeyEvent e) {
        return e.getAction() == KeyEvent.ACTION_DOWN ? onKeyDown(e.getKeyCode(), e) : onKeyUp(e.getKeyCode(), e);
    }

    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        return false;
    }

    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        return new android.window.OnBackInvokedDispatcher() {
            @Override
            public void registerOnBackInvokedCallback(int priority, android.window.OnBackInvokedCallback cb) {
                backCallback = cb;
            }

            @Override
            public void unregisterOnBackInvokedCallback(android.window.OnBackInvokedCallback cb) {
                if (backCallback == cb) {
                    backCallback = null;
                }
            }
        };
    }

    public void startActivityForResult(Intent i, int req) {
        desk.Shell.start(this, i, req);
    }

    public void startActivityForResult(Intent i, int req, Bundle options) {
        desk.Shell.start(this, i, req);
    }

    @Override
    public void startActivity(Intent i) {
        desk.Shell.start(this, i, -1);
    }

    public final void setResult(int code) {
        resultCode = code;
    }

    public final void setResult(int code, Intent data) {
        resultCode = code;
        resultData = data;
    }

    public void finish() {
        desk.Shell.finish(this);
    }

    public void finishAffinity() {
        desk.Shell.exit();
    }

    public void finishAndRemoveTask() {
        desk.Shell.exit();
    }

    public void finishAfterTransition() {
        finish();
    }

    public boolean isTaskRoot() {
        return false;
    }

    public boolean isFinishing() {
        return finishing;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void recreate() {
        desk.Shell.recreate(this);
    }

    public boolean moveTaskToBack(boolean nonRoot) {
        desk.Shell.minimize();
        return true;
    }

    public void overridePendingTransition(int in, int out) {
    }

    public void overrideActivityTransition(int type, int in, int out) {
    }

    public void setRequestedOrientation(int o) {
    }

    public int getRequestedOrientation() {
        return -1;
    }

    public boolean isInMultiWindowMode() {
        return false;
    }

    public boolean isChangingConfigurations() {
        return false;
    }

    public void setTitle(CharSequence t) {
    }

    public void setVolumeControlStream(int s) {
    }

    public void setTaskDescription(Object d) {
    }

    public void setShowWhenLocked(boolean b) {
    }

    public void setTurnScreenOn(boolean b) {
    }

    public android.content.ComponentName getComponentName() {
        return new android.content.ComponentName(this, getClass());
    }

    public String getLocalClassName() {
        return getClass().getSimpleName();
    }

    public final void runOnUiThread(Runnable r) {
        if (desk.Rt.isMain()) {
            r.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(r);
        }
    }

    public void requestPermissions(final String[] perms, final int req) {
        desk.Main.H.post(new Runnable() {
            @Override
            public void run() {
                int[] ok = new int[perms.length];
                onRequestPermissionsResult(req, perms, ok);
            }
        });
    }

    public boolean shouldShowRequestPermissionRationale(String p) {
        return false;
    }

    public android.media.session.MediaController getMediaController() {
        return null;
    }

    public void setMediaController(android.media.session.MediaController c) {
    }
}
