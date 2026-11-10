package dev.scratchwheel;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Cola de reproducción: lo que suena ahora y lo que viene a continuación. */
public class QueueActivity extends Activity {
    private Lucent th;
    private TextView nowView;
    private TextView emptyView;
    private BaseAdapter adapter;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onChanged() {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
            return;
        }
        Player.I.addListener(listener);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Player.I.removeListener(listener);
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private TextView text(float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        if (face == th.mono) {
            t.setLetterSpacing(0.04f);
            t.setLineSpacing(0, 1.25f);
        }
        return t;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText("COLA");
        title.setPadding(dp(8), 0, 0, 0);
        root.addView(title);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(th.card(Lucent.R_L));
        panel.setPadding(dp(16), dp(14), dp(16), dp(8));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        plp.topMargin = dp(12);

        TextView l1 = text(11, th.ink2, th.mono);
        l1.setText("SUENA AHORA");
        panel.addView(l1);
        nowView = text(16, th.ink, th.semi);
        nowView.setSingleLine(true);
        nowView.setEllipsize(TextUtils.TruncateAt.END);
        nowView.setPadding(0, dp(4), 0, dp(14));
        panel.addView(nowView);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView l2 = text(11, th.ink2, th.mono);
        l2.setText("A CONTINUACIÓN");
        head.addView(l2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = new Button(this);
        clear.setText("VACIAR");
        th.style(clear, Lucent.GLASS);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.clearQueue();
            }
        });
        Button save = new Button(this);
        save.setText("GUARDAR");
        th.style(save, Lucent.GLASS);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                java.util.List<String> all = new java.util.ArrayList<String>();
                if (Player.I.currentUri() != null) {
                    all.add(Player.I.currentUri().toString());
                }
                for (Player.Track t : Player.I.queue) {
                    all.add(t.uri.toString());
                }
                if (all.isEmpty()) {
                    return;
                }
                PlaylistUi.createWith(QueueActivity.this, all, null);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(96), dp(36));
        slp.rightMargin = dp(8);
        head.addView(save, slp);
        head.addView(clear, new LinearLayout.LayoutParams(dp(88), dp(36)));
        panel.addView(head);

        emptyView = text(13, th.ink2, th.regular);
        emptyView.setText("La cola está vacía. Mantén pulsada una canción de la biblioteca "
                + "para añadirla.");
        emptyView.setPadding(0, dp(14), 0, 0);
        panel.addView(emptyView);

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(th.ghost));
        list.setDividerHeight(1);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return Player.I.queue.size();
            }

            @Override
            public Object getItem(int pos) {
                return Player.I.queue.get(pos);
            }

            @Override
            public long getItemId(int pos) {
                return pos;
            }

            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert instanceof TextView ? (TextView) convert
                        : text(14, th.ink, th.regular);
                t.setPadding(0, dp(12), 0, dp(12));
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                SpannableString sp = new SpannableString((pos + 1) + "   "
                        + Player.I.queue.get(pos).display());
                int n = String.valueOf(pos + 1).length();
                sp.setSpan(new ForegroundColorSpan(th.ink2), 0, n, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                t.setText(sp);
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Player.I.playQueueItem(pos);
                finish();
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, final View view, final int pos, long id) {
                DotMenu.show(QueueActivity.this, th, view, new String[] {"AÑADIR A LISTA…", "QUITAR DE LA COLA"},
                        new DotMenu.Picked() {
                            @Override
                            public void onPick(int which) {
                                if (which == 0) {
                                    final java.util.List<String> one = new java.util.ArrayList<String>();
                                    one.add(Player.I.queue.get(pos).uri.toString());
                                    view.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            PlaylistUi.pick(QueueActivity.this, th, view, one, null);
                                        }
                                    });
                                } else {
                                    Player.I.removeFromQueue(pos);
                                }
                            }
                        });
                return true;
            }
        });
        panel.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = text(11, th.ink2, th.regular);
        hint.setText("Toca para reproducir ya · mantén pulsado para más opciones");
        hint.setPadding(0, dp(8), 0, dp(4));
        panel.addView(hint);
        root.addView(panel, plp);
        return root;
    }

    private void refresh() {
        Player p = Player.I;
        nowView.setText(p.title().length() == 0 ? "—" : p.title());
        emptyView.setVisibility(p.queue.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }
}
