package android.content;

import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;

public class Intent {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SENDTO = "android.intent.action.SENDTO";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE";
    public static final String ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT";
    public static final String ACTION_PICK = "android.intent.action.PICK";
    public static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
    public static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String CATEGORY_HOME = "android.intent.category.HOME";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES";
    public static final String EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE";
    public static final String EXTRA_LOCAL_ONLY = "android.intent.extra.LOCAL_ONLY";
    public static final String EXTRA_KEY_EVENT = "android.intent.extra.KEY_EVENT";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 2;
    public static final int FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x40;
    public static final int FLAG_GRANT_PREFIX_URI_PERMISSION = 0x80;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
    public static final int FLAG_ACTIVITY_NO_ANIMATION = 0x00010000;
    public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000;
    public static final int FLAG_ACTIVITY_NO_HISTORY = 0x40000000;
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000;

    private String action;
    private Uri data;
    private String type;
    private Bundle extras;
    private Class<?> cls;
    private ComponentName component;
    private String pkg;
    private int flags;
    private ClipData clip;
    private final ArrayList<String> categories = new ArrayList<String>();
    /** Solo escritorio: el título del selector cuando viene de createChooser. */
    public String chooserTitle;

    public Intent() {
    }

    public Intent(String action) {
        this.action = action;
    }

    public Intent(String action, Uri data) {
        this.action = action;
        this.data = data;
    }

    public Intent(Context c, Class<?> cls) {
        this.cls = cls;
    }

    public Intent(String action, Uri data, Context c, Class<?> cls) {
        this.action = action;
        this.data = data;
        this.cls = cls;
    }

    public Intent(Intent o) {
        action = o.action;
        data = o.data;
        type = o.type;
        cls = o.cls;
        component = o.component;
        pkg = o.pkg;
        flags = o.flags;
        clip = o.clip;
        categories.addAll(o.categories);
        if (o.extras != null) {
            extras = new Bundle(o.extras);
        }
    }

    public static Intent createChooser(Intent target, CharSequence title) {
        Intent i = new Intent(target);
        i.chooserTitle = title == null ? null : title.toString();
        return i;
    }

    public Class<?> targetClass() {
        if (cls != null) {
            return cls;
        }
        if (component != null) {
            try {
                return Class.forName(component.getClassName());
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    public String getAction() {
        return action;
    }

    public Intent setAction(String a) {
        action = a;
        return this;
    }

    public Uri getData() {
        return data;
    }

    public String getDataString() {
        return data == null ? null : data.toString();
    }

    public Intent setData(Uri u) {
        data = u;
        type = null;
        return this;
    }

    public String getType() {
        return type;
    }

    public Intent setType(String t) {
        type = t;
        data = null;
        return this;
    }

    public Intent setDataAndType(Uri u, String t) {
        data = u;
        type = t;
        return this;
    }

    public Intent setClass(Context c, Class<?> k) {
        cls = k;
        return this;
    }

    public Intent setClassName(Context c, String name) {
        component = new ComponentName(c, name);
        return this;
    }

    public Intent setComponent(ComponentName n) {
        component = n;
        return this;
    }

    public ComponentName getComponent() {
        return component;
    }

    public Intent setPackage(String p) {
        pkg = p;
        return this;
    }

    public String getPackage() {
        return pkg;
    }

    public Intent addCategory(String c) {
        categories.add(c);
        return this;
    }

    public boolean hasCategory(String c) {
        return categories.contains(c);
    }

    public Intent addFlags(int f) {
        flags |= f;
        return this;
    }

    public Intent setFlags(int f) {
        flags = f;
        return this;
    }

    public int getFlags() {
        return flags;
    }

    public ClipData getClipData() {
        return clip;
    }

    public void setClipData(ClipData c) {
        clip = c;
    }

    private Bundle ex() {
        if (extras == null) {
            extras = new Bundle();
        }
        return extras;
    }

    public Bundle getExtras() {
        return extras;
    }

    public Intent putExtras(Bundle b) {
        ex().putAll(b);
        return this;
    }

    public Intent putExtras(Intent o) {
        if (o.extras != null) {
            ex().putAll(o.extras);
        }
        return this;
    }

    public boolean hasExtra(String k) {
        return extras != null && extras.containsKey(k);
    }

    public Intent removeExtra(String k) {
        if (extras != null) {
            extras.remove(k);
        }
        return this;
    }

    public Intent putExtra(String k, String v) {
        ex().putString(k, v);
        return this;
    }

    public Intent putExtra(String k, CharSequence v) {
        ex().putCharSequence(k, v);
        return this;
    }

    public Intent putExtra(String k, int v) {
        ex().putInt(k, v);
        return this;
    }

    public Intent putExtra(String k, long v) {
        ex().putLong(k, v);
        return this;
    }

    public Intent putExtra(String k, float v) {
        ex().putFloat(k, v);
        return this;
    }

    public Intent putExtra(String k, double v) {
        ex().putDouble(k, v);
        return this;
    }

    public Intent putExtra(String k, boolean v) {
        ex().putBoolean(k, v);
        return this;
    }

    public Intent putExtra(String k, String[] v) {
        ex().putStringArray(k, v);
        return this;
    }

    public Intent putExtra(String k, int[] v) {
        ex().putIntArray(k, v);
        return this;
    }

    public Intent putExtra(String k, Bundle v) {
        ex().putBundle(k, v);
        return this;
    }

    public Intent putExtra(String k, Uri v) {
        ex().putParcelable(k, v);
        return this;
    }

    public Intent putExtra(String k, java.io.Serializable v) {
        ex().putSerializable(k, v);
        return this;
    }

    public Intent putStringArrayListExtra(String k, ArrayList<String> v) {
        ex().putStringArrayList(k, v);
        return this;
    }

    public String getStringExtra(String k) {
        return extras == null ? null : extras.getString(k);
    }

    public CharSequence getCharSequenceExtra(String k) {
        return extras == null ? null : extras.getCharSequence(k);
    }

    public int getIntExtra(String k, int def) {
        return extras == null ? def : extras.getInt(k, def);
    }

    public long getLongExtra(String k, long def) {
        return extras == null ? def : extras.getLong(k, def);
    }

    public float getFloatExtra(String k, float def) {
        return extras == null ? def : extras.getFloat(k, def);
    }

    public double getDoubleExtra(String k, double def) {
        return extras == null ? def : extras.getDouble(k, def);
    }

    public boolean getBooleanExtra(String k, boolean def) {
        return extras == null ? def : extras.getBoolean(k, def);
    }

    public String[] getStringArrayExtra(String k) {
        return extras == null ? null : extras.getStringArray(k);
    }

    public int[] getIntArrayExtra(String k) {
        return extras == null ? null : extras.getIntArray(k);
    }

    public ArrayList<String> getStringArrayListExtra(String k) {
        return extras == null ? null : extras.getStringArrayList(k);
    }

    public Bundle getBundleExtra(String k) {
        return extras == null ? null : extras.getBundle(k);
    }

    public <T> T getParcelableExtra(String k) {
        return extras == null ? null : extras.<T>getParcelable(k);
    }

    public <T> T getParcelableExtra(String k, Class<T> c) {
        return extras == null ? null : extras.<T>getParcelable(k);
    }

    public java.io.Serializable getSerializableExtra(String k) {
        return extras == null ? null : extras.getSerializable(k);
    }

    @Override
    public String toString() {
        return "Intent{" + action + " " + data + " " + (cls == null ? component : cls.getSimpleName()) + "}";
    }
}
