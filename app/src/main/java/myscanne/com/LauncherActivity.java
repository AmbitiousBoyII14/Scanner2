package myscanne.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class LauncherActivity extends Activity {

    private TextView tvTitle, tvDays, tvStatus;
    private EditText etKey;
    private Button btnAdmin;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!G.q(this)) {
            Toast.makeText(this, X.p(24), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = getIntent();

        boolean forceShow = intent != null && intent.getBooleanExtra("forceShowLauncher", false);
        boolean openAdmin = intent != null && intent.getBooleanExtra("openAdmin", false);

        if (G.a(this) && !forceShow) {
            if (G.b(this)) {
                go();
                return;
            }

            z1();
            return;
        }

        ui();

        if (openAdmin) {
            z3();
        }
    }

    private void go() {
        Intent in = new Intent(this, MainActivity.class);

        in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(in);
        finish();
    }

    private void z1() {
        final ProgressDialog pd = new ProgressDialog(this);

        pd.setMessage(X.p(21));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = G.o(LauncherActivity.this);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();

                        if (ok) {
                            go();
                        } else {
                            G.h(LauncherActivity.this);

                            Toast.makeText(
                                    LauncherActivity.this,
                                    X.p(23),
                                    Toast.LENGTH_LONG
                            ).show();

                            ui();
                        }
                    }
                });
            }
        }).start();
    }

    private void z2() {
        final String key = etKey.getText().toString().trim().toUpperCase(Locale.US);

        if (key.length() < 8) {
            etKey.setError("...");
            return;
        }

        final ProgressDialog pd = new ProgressDialog(this);

        pd.setMessage(X.p(22));
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final G.R r = G.n(LauncherActivity.this, key);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();

                        if (r.x == 0) {
                            G.f(LauncherActivity.this, key, r.y, r.z);

                            Toast.makeText(
                                    LauncherActivity.this,
                                    ("t".equals(r.y) ? X.p(43) : X.p(44)) + "!",
                                    Toast.LENGTH_SHORT
                            ).show();

                            go();
                        } else {
                            etKey.setError(G.p(r.x));
                        }
                    }
                });
            }
        }).start();
    }

    private void tg() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(X.c())));
        } catch (Exception e) {
            Toast.makeText(this, X.p(16), Toast.LENGTH_LONG).show();
        }
    }

    private void ui() {
        final int accent = Prefs.accent(this);
        final int info = Prefs.info(this);
        final int muted = Prefs.muted(this);
        final int[] fl = G.i(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Prefs.bg(this));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(40), dp(24), dp(24));

        sv.addView(content);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER);

        tvTitle = new TextView(this);
        tvTitle.setText(X.p(20));
        tvTitle.setTextColor(accent);
        tvTitle.setTextSize(28f);
        tvTitle.setTypeface(null, Typeface.BOLD);

        titleRow.addView(tvTitle);

        tvDays = new TextView(this);
        tvDays.setTextSize(11f);
        tvDays.setTypeface(null, Typeface.BOLD);
        tvDays.setPadding(dp(8), dp(3), dp(8), dp(3));

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        dlp.leftMargin = dp(10);
        tvDays.setLayoutParams(dlp);

        titleRow.addView(tvDays);

        badge();

        content.addView(titleRow);

        TextView tvSub = new TextView(this);
        tvSub.setText(X.p(19));
        tvSub.setTextColor(muted);
        tvSub.setTextSize(13f);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, dp(6), 0, dp(16));

        content.addView(tvSub);

        etKey = new EditText(this);
        etKey.setHint("XXXX-XXXX-XXXX-XXXX");
        etKey.setTextColor(Prefs.text(this));
        etKey.setHintTextColor(muted);
        etKey.setTypeface(Typeface.MONOSPACE);
        etKey.setGravity(Gravity.CENTER);
        etKey.setBackgroundDrawable(Theme.input(this));
        etKey.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        klp.bottomMargin = dp(12);
        etKey.setLayoutParams(klp);

        content.addView(etKey);

        Button btnGo = new Button(this);
        btnGo.setText(X.p(18));
        btnGo.setTextColor(Theme.onColor(accent));
        btnGo.setTextSize(15f);
        btnGo.setTypeface(null, Typeface.BOLD);
        btnGo.setBackgroundDrawable(Theme.filled(this, accent));

        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        alp.bottomMargin = dp(10);
        btnGo.setLayoutParams(alp);

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                z2();
            }
        });

        content.addView(btnGo);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(muted);
        tvStatus.setTextSize(12f);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dp(2), 0, dp(14));

        content.addView(tvStatus);

        status();

        content.addView(card(
                X.p(1),
                muted,
                !G.a(this),
                new String[]{X.p(5), X.p(6), X.p(7), X.p(8)},
                null,
                0
        ));

        content.addView(card(
                X.p(2),
                accent,
                G.a(this) && !G.b(this),
                new String[]{X.p(9), X.p(10), X.p(11), X.p(12)},
                X.p(16),
                accent
        ));

        content.addView(card(
                X.p(3),
                info,
                G.b(this),
                new String[]{X.p(13), X.p(14), X.p(15)},
                X.p(17),
                info
        ));

        Button btnFree = new Button(this);
        btnFree.setText(X.q(1, fl[0], fl[1]));
        btnFree.setTextColor(info);
        btnFree.setTextSize(13f);
        btnFree.setBackgroundDrawable(Theme.outline(this, info));

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        flp.topMargin = dp(4);
        flp.bottomMargin = dp(12);
        btnFree.setLayoutParams(flp);

        btnFree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                G.g(LauncherActivity.this);
                go();
            }
        });

        content.addView(btnFree);

        btnAdmin = new Button(this);
        btnAdmin.setText(X.p(45));
        btnAdmin.setTextColor(muted);
        btnAdmin.setTextSize(12f);
        btnAdmin.setBackgroundDrawable(Theme.outline(this, muted));
        btnAdmin.setVisibility(View.GONE);

        btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                z3();
            }
        });

        content.addView(btnAdmin);

        tvTitle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                btnAdmin.setVisibility(View.VISIBLE);
                return true;
            }
        });

        setContentView(sv);
    }

    private LinearLayout card(String title,
                                int color,
                                boolean current,
                                String[] lines,
                                String btn,
                                int btnColor) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(this));
        g.setCornerRadius(dp(12));
        g.setStroke(dp(current ? 2 : 1), current ? color : Prefs.stroke(this));

        card.setBackgroundDrawable(g);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(color);
        t.setTextSize(15f);
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        row.addView(t);

        if (current) {
            TextView cur = new TextView(this);
            cur.setText(X.p(4));
            cur.setTextColor(color);
            cur.setTextSize(10f);
            cur.setTypeface(null, Typeface.BOLD);

            row.addView(cur);
        }

        card.addView(row);

        for (int i = 0; i < lines.length; i++) {
            TextView tv = new TextView(this);
            tv.setText("• " + lines[i]);
            tv.setTextColor(Prefs.muted(this));
            tv.setTextSize(12f);
            tv.setPadding(0, dp(3), 0, 0);

            card.addView(tv);
        }

        if (btn != null) {
            Button b = new Button(this);
            b.setText(btn);
            b.setTextColor(btnColor);
            b.setTextSize(13f);
            b.setTypeface(null, Typeface.BOLD);
            b.setBackgroundDrawable(Theme.outline(this, btnColor));

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            blp.topMargin = dp(10);
            b.setLayoutParams(blp);

            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tg();
                }
            });

            card.addView(b);
        }

        return card;
    }

    private void badge() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(6));

        if (G.a(this)) {
            tvDays.setText(G.e(this));

            boolean urgent = G.d(this) >= 0 && G.d(this) < 3L * 24 * 3600 * 1000;

            g.setColor(urgent ? 0x33FF3B4E : 0x3322C55E);
            tvDays.setTextColor(urgent ? 0xFFFF3B4E : 0xFF22C55E);
        } else {
            tvDays.setText(G.e(this));

            g.setColor(0x338A93A6);
            tvDays.setTextColor(Prefs.muted(this));
        }

        tvDays.setBackgroundDrawable(g);
    }

    private void status() {
        if (G.a(this)) {
            tvStatus.setText((G.b(this) ? X.p(43) : X.p(44)) + X.p(42) + G.e(this));
            tvStatus.setTextColor(0xFF22C55E);
        } else {
            int[] fl = G.i(this);
            tvStatus.setText(X.q(2, fl[0], fl[1]));
        }
    }

    private void z3() {
        Toast.makeText(this, "Admin panel is not configured yet", Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}