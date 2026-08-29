package myscanne.com;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private LinearLayout root, header, container, methodSeg;

    private Button tabUi, tabTheme, tabScan, tabTools;

    private TextView tvTitle, btnBack;

    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        root = (LinearLayout) findViewById(R.id.settingsRoot);
        header = (LinearLayout) findViewById(R.id.settingsHeader);
        container = (LinearLayout) findViewById(R.id.settingsContainer);

        tabUi = (Button) findViewById(R.id.tabUi);
        tabTheme = (Button) findViewById(R.id.tabTheme);
        tabScan = (Button) findViewById(R.id.tabScan);
        tabTools = (Button) findViewById(R.id.tabTools);

        tvTitle = (TextView) findViewById(R.id.tvTitle);
        btnBack = (TextView) findViewById(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabUi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 0;
                render();
            }
        });

        tabTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 1;
                render();
            }
        });

        tabScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 2;
                render();
            }
        });

        tabTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = 3;
                render();
            }
        });

        render();
    }

    private void render() {
        root.setBackgroundColor(Prefs.bg(this));
        header.setBackgroundColor(Prefs.card(this));

        tvTitle.setTextColor(Prefs.text(this));
        btnBack.setTextColor(Prefs.accent(this));

        styleTab(tabUi, currentTab == 0);
        styleTab(tabTheme, currentTab == 1);
        styleTab(tabScan, currentTab == 2);
        styleTab(tabTools, currentTab == 3);

        container.removeAllViews();

        if (currentTab == 0) buildUiTab();
        else if (currentTab == 1) buildThemeTab();
        else if (currentTab == 2) buildScanTab();
        else buildToolsTab();
    }

    private void styleTab(Button b, boolean active) {
        if (active) {
            b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
            b.setTextColor(Theme.onColor(Prefs.accent(this)));
        } else {
            b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
            b.setTextColor(Prefs.muted(this));
        }
    }

    private void buildUiTab() {
        addSectionLabel("APPEARANCE");

        addSegment("Font", new String[]{"Monospace", "Sans"}, Prefs.font(this),
                new OnPick() {
                    @Override
                    public void pick(int i) {
                        Prefs.setFont(SettingsActivity.this, i);
                        render();
                    }
                });

        addSegment("Density", new String[]{"Comfortable", "Compact"}, Prefs.density(this),
                new OnPick() {
                    @Override
                    public void pick(int i) {
                        Prefs.setDensity(SettingsActivity.this, i);
                        render();
                    }
                });

        addSegment("Menu Layout", new String[]{"List", "Grid"}, Prefs.menu(this),
                new OnPick() {
                    @Override
                    public void pick(int i) {
                        Prefs.setMenu(SettingsActivity.this, i);
                        render();
                    }
                });

        addHint("Changes apply across the app instantly. Open a scanner or the home screen to see them.");
    }

    private void buildThemeTab() {
        addSectionLabel("COLOR THEME");

        int selected = Prefs.theme(this);

        for (int i = 0; i < Prefs.TN.length; i++) {
            final int index = i;

            int[] pal = Prefs.palette(i);

            boolean active = i == selected;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundDrawable(makeCardWith(pal[1], active ? pal[3] : pal[2], active ? 2 : 1));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

            lp.topMargin = dp(10);
            row.setLayoutParams(lp);
            row.setClickable(true);

            row.addView(swatch(pal[3]));
            row.addView(swatch(pal[4]));
            row.addView(swatch(pal[0]));

            LinearLayout nameWrap = new LinearLayout(this);
            nameWrap.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );

            nlp.leftMargin = dp(12);
            nameWrap.setLayoutParams(nlp);

            TextView name = new TextView(this);
            name.setText(Prefs.TN[i]);
            name.setTextColor(pal[5]);
            name.setTextSize(15f);
            Theme.applyFont(this, name, Typeface.BOLD);

            nameWrap.addView(name);

            TextView meta = new TextView(this);
            meta.setText(Prefs.themeKind(i) + " \u00B7 " + Prefs.themeVersion(i));
            meta.setTextColor(pal[6]);
            meta.setTextSize(11f);
            Theme.applyFont(this, meta, Typeface.NORMAL);

            nameWrap.addView(meta);

            row.addView(nameWrap);

            TextView check = new TextView(this);
            check.setText(active ? "\u2713" : "");
            check.setTextColor(pal[3]);
            check.setTextSize(20f);
            Theme.applyFont(this, check, Typeface.BOLD);

            row.addView(check);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setTheme(SettingsActivity.this, index);
                    render();
                }
            });

            container.addView(row);
        }

        addHint("Dark themes suit AMOLED & low light; Light themes are bright. Newer themes are marked v2.");
    }

    private void buildScanTab() {
        addSectionLabel("SCAN DEFAULTS");

        double currentTimeoutSec = Prefs.getTimeoutMs(this) / 1000.0;

        final EditText etTimeout = addEditRow(
                "Timeout (s)",
                String.valueOf(currentTimeoutSec).replaceAll("\\.0$", ""),
                "0.2 - 60  (default 1.0)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        final EditText etPorts = addEditRow(
                "Ports",
                Prefs.getPorts(this),
                "comma-separated, e.g. 443,80,8080",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        methodSeg = addSegmentRow(
                "HTTP Method",
                new String[]{"GET", "HEAD"},
                Prefs.getMethod(this),
                new OnPick() {
                    @Override
                    public void pick(int i) {
                        Prefs.setMethod(SettingsActivity.this, i);
                        updateSegmentVisuals(methodSeg, i);
                    }
                }
        );

        final EditText etThreads = addEditRow(
                "Threads (worker pool)",
                String.valueOf(Prefs.getThreads(this)),
                "1 - 500  (default 50)",
                InputType.TYPE_CLASS_NUMBER
        );

        final EditText etDelay = addEditRow(
                "Delay per host (ms)",
                String.valueOf(Prefs.getDelayMs(this)),
                "0 - 1000  (default 0)",
                InputType.TYPE_CLASS_NUMBER
        );

        Button btnSave = new Button(this);
        btnSave.setText("SAVE SCAN SETTINGS");
        btnSave.setAllCaps(false);
        btnSave.setTextSize(14f);
        Theme.applyFont(this, btnSave, Typeface.BOLD);
        btnSave.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
        btnSave.setTextColor(Theme.onColor(Prefs.accent(this)));

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        slp.topMargin = dp(20);
        btnSave.setLayoutParams(slp);

        final EditText fTimeout = etTimeout;
        final EditText fPorts = etPorts;
        final EditText fThreads = etThreads;
        final EditText fDelay = etDelay;

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ok = true;

                try {
                    double sec = Double.parseDouble(fTimeout.getText().toString().trim());
                    int ms = (int) Math.round(sec * 1000);

                    if (ms < 200 || ms > 60000) throw new IllegalArgumentException();

                    Prefs.setTimeoutMs(SettingsActivity.this, ms);
                } catch (Exception e) {
                    fTimeout.setError("Enter a value between 0.2 and 60 seconds");
                    ok = false;
                }

                String portStr = fPorts.getText().toString().trim();

                if (portStr.length() == 0) {
                    portStr = "443";
                }

                boolean portsOk = true;

                for (String p : portStr.split(",")) {
                    try {
                        int pn = Integer.parseInt(p.trim());

                        if (pn < 1 || pn > 65535) {
                            portsOk = false;
                            break;
                        }
                    } catch (Exception e) {
                        portsOk = false;
                        break;
                    }
                }

                if (!portsOk) {
                    fPorts.setError("Use comma-separated numbers 1\u201365535");
                    ok = false;
                } else {
                    Prefs.setPorts(SettingsActivity.this, portStr);
                }

                try {
                    int t = Integer.parseInt(fThreads.getText().toString().trim());

                    if (t < 1 || t > 500) throw new IllegalArgumentException();

                    Prefs.setThreads(SettingsActivity.this, t);
                } catch (Exception e) {
                    fThreads.setError("Enter a value between 1 and 500");
                    ok = false;
                }

                try {
                    int d = Integer.parseInt(fDelay.getText().toString().trim());

                    if (d < 0 || d > 1000) throw new IllegalArgumentException();

                    Prefs.setDelayMs(SettingsActivity.this, d);
                } catch (Exception e) {
                    fDelay.setError("Enter a value between 0 and 1000");
                    ok = false;
                }

                if (ok) {
                    Toast.makeText(
                            SettingsActivity.this,
                            "Scan settings saved",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });

        container.addView(btnSave);

        addCopyright("Syamthanda : Telegram @Treacky_1");
    }

    private void buildToolsTab() {
        addSectionLabel("OFFLINE (no external services)");

        addToolRow("BugHost Probe", "WAF + tech + takeover + WS + endpoints + ports + cert in one.");
        addToolRow("DPI Scanner", "Detects deep-packet-inspection / SNI blocking (fragmented handshake).");
        addToolRow("WS Tester", "Tests WebSocket upgrade handshake acceptance.");
        addToolRow("SNI Scanner", "Talks direct TLS to a fixed front (google.com) with your domain as SNI.");
        addToolRow("TLS Scanner", "Fetches HTTP status + server banner over TLS.");
        addToolRow("Proxy Scanner", "Checks if a host acts as a proxy (optional SNI mask).");
        addToolRow("Port Checker", "Scans a configured list of TCP ports with service detection.");
        addToolRow("Tech Fingerprint", "Identifies server, framework, CMS and CDN from headers.");
        addToolRow("DNS Lookup", "Resolves A/AAAA/CNAME/MX/NS/TXT records.");
        addToolRow("Security Headers", "Scores HTTP security headers and lists missing ones.");
        addToolRow("HTTP Version", "Probes HTTP/1.1, HTTP/2 and HTTP/3 support.");
        addToolRow("CDN Checker", "Detects the CDN provider from response headers.");
        addToolRow("Ping Test", "TCP ping with min/avg/max latency stats.");
        addToolRow("SSL Certificate", "Shows cert details, expiry and SAN names.");
        addToolRow("Redirect Tracer", "Follows the HTTP redirect chain to its final URL.");

        addSectionLabel("ONLINE (uses external APIs)");

        addToolRow("Deep Enumeration", "Collects subdomains from crt.sh, Certspotter, AlienVault, HackerTarget.");
        addToolRow("Takeover Check", "Finds CNAMEs pointing at dangling services (subdomain takeover).");
        addToolRow("Endpoint Fuzzer", "Fuzzes a list of sensitive/common paths and reports exposed ones.");
        addToolRow("Wayback URLs", "Pulls historical URLs from the Wayback Machine + juicy filter.");
        addToolRow("Subdomain Finder", "Enumerates subdomains via CT logs (crt.sh).");
        addToolRow("Hosts Finder", "Finds domains for a whole TLD via crt.sh CT logs.");
        addToolRow("Reverse IP", "PTR + multi-source reverse-IP lookup to find domains sharing an IP.");
        addToolRow("CIDR Enumerator", "Expands CIDR to IPs, gets PTRs, detects CDN/hoster range, then optional reverse IP lookup.");
        addToolRow("IP Geolocation", "Country, city, ISP and ASN for an IP (ip-api.com).");
        addToolRow("Whois Lookup", "RDAP domain registration data.");

        addSectionLabel("UTILITIES");

        addToolRow("TXT Splitter", "Splits a large list file into smaller 25k-line parts.");
    }

    private void addToolRow(String name, String desc) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(Prefs.text(this));
        tvName.setTextSize(14f);
        Theme.applyFont(this, tvName, Typeface.BOLD);

        wrap.addView(tvName);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextColor(Prefs.muted(this));
        tvDesc.setTextSize(12f);
        Theme.applyFont(this, tvDesc, Typeface.NORMAL);
        tvDesc.setPadding(0, dp(2), 0, 0);

        wrap.addView(tvDesc);

        container.addView(wrap);
    }

    private interface OnPick {
        void pick(int index);
    }

    private EditText addEditRow(String label, String value, String hint, int inputType) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Prefs.text(this));
        tvLabel.setTextSize(14f);
        Theme.applyFont(this, tvLabel, Typeface.BOLD);

        wrap.addView(tvLabel);

        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextColor(Prefs.text(this));
        et.setHintTextColor(Prefs.muted(this));
        et.setBackgroundDrawable(Theme.input(this));
        et.setTextSize(14f);
        Theme.applyFont(this, et, Typeface.NORMAL);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        elp.topMargin = dp(6);
        et.setLayoutParams(elp);

        wrap.addView(et);

        container.addView(wrap);

        return et;
    }

    private void addSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        Theme.applyFont(this, tv, Typeface.BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        lp.topMargin = dp(6);
        lp.bottomMargin = dp(2);

        tv.setLayoutParams(lp);

        container.addView(tv);
    }

    private void addSegment(String label, String[] options, int selected, final OnPick cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Prefs.text(this));
        tvLabel.setTextSize(14f);
        Theme.applyFont(this, tvLabel, Typeface.BOLD);

        wrap.addView(tvLabel);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        slp.topMargin = dp(8);
        seg.setLayoutParams(slp);

        for (int i = 0; i < options.length; i++) {
            final int idx = i;

            boolean active = i == selected;

            Button b = new Button(this);
            b.setText(options[i]);
            b.setAllCaps(false);
            b.setTextSize(13f);
            Theme.applyFont(this, b, Typeface.BOLD);

            if (active) {
                b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
                b.setTextColor(Theme.onColor(Prefs.accent(this)));
            } else {
                b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                b.setTextColor(Prefs.muted(this));
            }

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );

            if (i > 0) blp.leftMargin = dp(6);

            b.setLayoutParams(blp);

            seg.addView(b);

            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.pick(idx);
                }
            });
        }

        wrap.addView(seg);

        container.addView(wrap);
    }

    private LinearLayout addSegmentRow(String label, String[] options, int selected, final OnPick cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Prefs.text(this));
        tvLabel.setTextSize(14f);
        Theme.applyFont(this, tvLabel, Typeface.BOLD);

        wrap.addView(tvLabel);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        slp.topMargin = dp(8);
        seg.setLayoutParams(slp);

        for (int i = 0; i < options.length; i++) {
            final int idx = i;

            boolean active = i == selected;

            Button b = new Button(this);
            b.setText(options[i]);
            b.setAllCaps(false);
            b.setTextSize(13f);
            Theme.applyFont(this, b, Typeface.BOLD);

            if (active) {
                b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
                b.setTextColor(Theme.onColor(Prefs.accent(this)));
            } else {
                b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                b.setTextColor(Prefs.muted(this));
            }

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );

            if (i > 0) blp.leftMargin = dp(6);

            b.setLayoutParams(blp);

            seg.addView(b);

            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.pick(idx);
                }
            });
        }

        wrap.addView(seg);

        container.addView(wrap);

        return seg;
    }

    private void updateSegmentVisuals(LinearLayout seg, int newIndex) {
        for (int i = 0; i < seg.getChildCount(); i++) {
            Button b = (Button) seg.getChildAt(i);

            boolean active = i == newIndex;

            if (active) {
                b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
                b.setTextColor(Theme.onColor(Prefs.accent(this)));
            } else {
                b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                b.setTextColor(Prefs.muted(this));
            }
        }
    }

    private void addHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        Theme.applyFont(this, tv, Typeface.NORMAL);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        tv.setLineSpacing(dp(4), 1f);

        container.addView(tv);
    }

    private void addCopyright(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        Theme.applyFont(this, tv, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp(8));
        border.setStroke(dp(1), Prefs.muted(this));
        border.setColor(android.graphics.Color.TRANSPARENT);

        tv.setBackgroundDrawable(border);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);

        tv.setLayoutParams(lp);

        container.addView(tv);
    }

    private GradientDrawable makeCardWith(int bg, int stroke, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();

        gd.setColor(bg);
        gd.setStroke(dp(strokeWidth), stroke);
        gd.setCornerRadius(dp(8));

        return gd;
    }

    private View swatch(int color) {
        View v = new View(this);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setShape(GradientDrawable.OVAL);

        v.setBackgroundDrawable(gd);

        int size = dp(24);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}