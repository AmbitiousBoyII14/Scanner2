package myscanne.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScannerActivity extends Activity {

    private static final int REQ_PICK = 3001;

    private int splitChunk = 25000;

    private int C_OK = 0xFF22C55E;
    private int C_WARN = 0xFFF59E0B;
    private int C_INFO = 0xFF3D8BFF;
    private int C_MUTED = 0xFF8A93A6;
    private int C_DANGER = 0xFFFF3B4E;

    private String mode = "TLS";
    private String title = "Scanner";

    private EditText etTarget, etSni;
    private Button btnSingle, btnFile, btnStop, btnSave;
    private TextView tvTitle, tvSubtitle, tvStatus, tvProgress, tvEta;
    private ProgressBar progressHorizontal;
    private ScrollView svResults;
    private LinearLayout resultsContainer;

    private volatile boolean cancelled;
    private volatile boolean running;

    private long startTime;
    private int totalCount;

    private final List<String> sessionHits =
            java.util.Collections.synchronizedList(new ArrayList<String>());

    private final List<String> sessionDetails =
            java.util.Collections.synchronizedList(new ArrayList<String>());
private static class Row {
    String m = "", c = "", s = "", p = "", ip = "", h = "";
}

private final List<Row> sessionRows =
        java.util.Collections.synchronizedList(new ArrayList<Row>());

private final java.util.Set<String> domSet =
        java.util.Collections.synchronizedSet(new LinkedHashSet<String>());

private final java.util.Set<String> ipSet =
        java.util.Collections.synchronizedSet(new LinkedHashSet<String>());
        
    private static final String[] OFFLINE_TOOLS = {
            "DPI", "WS", "SNI", "TLS", "PROXY", "PORT", "TECH",
            "DNS", "HEADERS", "HTTP_VER", "CDN", "PING", "CERT", "REDIRECT"
    };

    private static final String[] ONLINE_TOOLS = {
            "DEEPENUM", "TAKEOVER", "ENDPOINT", "WAYBACK",
            "SUBDOMAIN", "REVIP", "GEO", "WHOIS"
    };

    private static class ScanRate {
        private final long minMs;
        private long last = 0;

        ScanRate(int perSecond) {
            if (perSecond <= 0) {
                minMs = 0;
            } else {
                minMs = 1000L / perSecond;
            }
        }

        synchronized void one() {
            if (minMs <= 0) return;

            long now = System.currentTimeMillis();
            long wait = last + minMs - now;

            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException x) {
                }
            }

            last = System.currentTimeMillis();
        }
    }

    private static final ScanRate HOST_RATE = new ScanRate(18);
    private static final ScanRate PORT_RATE = new ScanRate(10);
    private static final ScanRate BUG_RATE = new ScanRate(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        if (getIntent() != null) {
            if (getIntent().getStringExtra("mode") != null) {
                mode = getIntent().getStringExtra("mode");
            }

            if (getIntent().getStringExtra("title") != null) {
                title = getIntent().getStringExtra("title");
            }
        }

        etTarget = (EditText) findViewById(R.id.etTarget);
        etSni = (EditText) findViewById(R.id.etSni);

        btnSingle = (Button) findViewById(R.id.btnSingle);
        btnFile = (Button) findViewById(R.id.btnFile);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnSave = (Button) findViewById(R.id.btnSave);

        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        tvEta = (TextView) findViewById(R.id.tvEta);

        progressHorizontal = (ProgressBar) findViewById(R.id.progressHorizontal);
        svResults = (ScrollView) findViewById(R.id.svResults);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        svResults.addView(resultsContainer);

        tvTitle.setText(title);

        applyTheme();
        configureMode();

        ScanEngine.setPorts(Prefs.getPortsArray(this));

        int[] fl = G.i(this);

        String lic = G.a(this)
                ? ((G.b(this) ? X.p(39) : X.p(2)) + " - " + G.e(this))
                : X.q(4, fl[0], fl[1]);

        tvTitle.setText(title + "  [" + lic + "]");

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSingle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("HOSTS".equals(mode)) {
                    startHosts();
                    return;
                }

                if ("DEEPENUM".equals(mode)) {
                    startDeep();
                    return;
                }

                if ("TAKEOVER".equals(mode)) {
                    startTk();
                    return;
                }

                if ("ENDPOINT".equals(mode)) {
                    startEp();
                    return;
                }

                if ("SPLIT".equals(mode)) {
                    return;
                }

                startSingle();
            }
        });

        btnFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelled = true;
                setStatus("Stopping...", C_WARN);
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveResults();
            }
        });
    }

    private void applyTheme() {
        C_MUTED = Prefs.muted(this);

        int t = Prefs.text(this);

        C_OK = 0xFF22C55E;
        C_WARN = 0xFFF59E0B;
        C_INFO = Prefs.accent(this);

        View root = findViewById(R.id.scannerRoot);
        View hdr = findViewById(R.id.scannerHeader);

        if (root != null) root.setBackgroundColor(Prefs.bg(this));
        if (hdr != null) hdr.setBackgroundColor(Prefs.card(this));

        ((TextView) findViewById(R.id.btnBack)).setTextColor(C_INFO);

        tvTitle.setTextColor(t);
        tvSubtitle.setTextColor(C_MUTED);
        tvStatus.setTextColor(C_MUTED);
        tvProgress.setTextColor(Prefs.info(this));
        tvEta.setTextColor(C_MUTED);

        int pad = Prefs.isCompact(this) ? dp(6) : dp(12);

        etTarget.setBackgroundDrawable(Theme.input(this));
        etTarget.setTextColor(t);
        etTarget.setHintTextColor(C_MUTED);

        etSni.setBackgroundDrawable(Theme.input(this));
        etSni.setTextColor(t);
        etSni.setHintTextColor(C_MUTED);

        btnSingle.setBackgroundDrawable(Theme.filled(this, C_INFO));
        btnSingle.setTextColor(Theme.onColor(C_INFO));

        btnFile.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
        btnFile.setTextColor(C_MUTED);

        btnStop.setBackgroundDrawable(Theme.outline(this, C_WARN));
        btnStop.setTextColor(C_WARN);

        btnSave.setBackgroundDrawable(Theme.filled(this, C_INFO));
        btnSave.setTextColor(Theme.onColor(C_INFO));

        svResults.setPadding(pad, pad, pad, pad);

        int resultBg = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this);

        GradientDrawable rg = new GradientDrawable();
        rg.setColor(resultBg);
        rg.setCornerRadius(dp(12));
        rg.setStroke(dp(1), Prefs.stroke(this));

        svResults.setBackgroundDrawable(rg);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void configureMode() {
        String cfg = " to=" + (Prefs.getTimeoutMs(this) / 1000.0) + "s th=" + Prefs.getThreads(this);

        etSni.setEnabled(true);

        if ("ALLOFFLINE".equals(mode)) {
            tvSubtitle.setText("Run ALL offline tools on a target\n" + cfg);
            etTarget.setHint("domain / ip / file");
            btnSingle.setText("SCAN ALL OFFLINE");
        } else if ("ALLONLINE".equals(mode)) {
            tvSubtitle.setText("Run ALL online tools on a target\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("SCAN ALL ONLINE");
        } else if ("BUGHOST".equals(mode)) {
            tvSubtitle.setText("Full probe: WAF+tech+takeover+WS+endpoints+ports+cert\n" + cfg);
            etTarget.setHint("domain or file");
            btnSingle.setText("PROBE");
        } else if ("WS".equals(mode)) {
            tvSubtitle.setText("WebSocket upgrade tester\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("TEST WS");
        } else if ("DPI".equals(mode)) {
            tvSubtitle.setText("Fragmented DPI bypass detection\n" + cfg);
            etTarget.setHint("host/ip");
        } else if ("SNI".equals(mode)) {
            tvSubtitle.setText("Direct SSL via Google + SNI host\n" + cfg);
            etTarget.setHint("domain");
            etSni.setHint("front (fixed)");
            etSni.setText(ScanEngine.SNI_IP);
            etSni.setEnabled(false);
            etSni.setVisibility(View.VISIBLE);
        } else if ("TLS".equals(mode)) {
            tvSubtitle.setText("HTTP status + server over TLS\n" + cfg);
            etTarget.setHint("domain");
        } else if ("PROXY".equals(mode)) {
            tvSubtitle.setText("Check host as proxy (optional SNI)\n" + cfg);
            etTarget.setHint("proxy host/ip");
            etSni.setHint("SNI mask");
            etSni.setVisibility(View.VISIBLE);
        } else if ("PORT".equals(mode)) {
            tvSubtitle.setText("Scan ports: " + Prefs.getPorts(this) + "\n" + cfg);
            etTarget.setHint("host/ip or CIDR (e.g. 192.168.1.0/24)");
        } else if ("SUBDOMAIN".equals(mode)) {
            tvSubtitle.setText("crt.sh + brute subdomain enum\n" + cfg);
            etTarget.setHint("domain");
        } else if ("REVIP".equals(mode)) {
            tvSubtitle.setText("Reverse IP lookup\n" + cfg);
            etTarget.setHint("ip/host");
        } else if ("CDN".equals(mode)) {
            tvSubtitle.setText("CDN provider detection\n" + cfg);
            etTarget.setHint("domain");
        } else if ("HEADERS".equals(mode)) {
            tvSubtitle.setText("Security headers + score\n" + cfg);
            etTarget.setHint("domain");
        } else if ("HTTP_VER".equals(mode)) {
            tvSubtitle.setText("HTTP/1.1, HTTP/2, HTTP/3 probe\n" + cfg);
            etTarget.setHint("domain");
        } else if ("DNS".equals(mode)) {
            tvSubtitle.setText("A, AAAA, CNAME, MX, NS, TXT records\n" + cfg);
            etTarget.setHint("domain");
        } else if ("TECH".equals(mode)) {
            tvSubtitle.setText("Server, framework, CMS, CDN\n" + cfg);
            etTarget.setHint("domain");
        } else if ("TAKEOVER".equals(mode)) {
            tvSubtitle.setText("Deep enum + CNAME takeover check\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("CHECK");
        } else if ("WAYBACK".equals(mode)) {
            tvSubtitle.setText("Wayback URLs + juicy filter\n" + cfg);
            etTarget.setHint("domain");
            etSni.setHint("Max URLs");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("5000");
            btnSingle.setText("FETCH");
        } else if ("ENDPOINT".equals(mode)) {
            tvSubtitle.setText("Fuzz " + ScanEngine.COMMON_PATHS.length + " paths\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("FUZZ");
        } else if ("DEEPENUM".equals(mode)) {
            tvSubtitle.setText("4-source: crt+certspotter+alienvault+HT\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("ENUM");
        } else if ("HOSTS".equals(mode)) {
            tvSubtitle.setText("TLD domains via crt.sh\n" + cfg);
            etTarget.setHint("TLD");
            etSni.setHint("Count");
            etSni.setVisibility(View.VISIBLE);
            btnSingle.setText("FIND");
        } else if ("SPLIT".equals(mode)) {
            tvSubtitle.setText("Split " + splitChunk + "-line parts\n" + cfg);
            etTarget.setHint("Lines per part (default 25000)");
            btnSingle.setVisibility(View.GONE);
            btnFile.setText("PICK & SPLIT");
        } else if ("PING".equals(mode)) {
            tvSubtitle.setText("TCP ping with latency stats\n" + cfg);
            etTarget.setHint("domain/ip");
            etSni.setHint("Count (default 4)");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("4");
            btnSingle.setText("PING");
        } else if ("CERT".equals(mode)) {
            tvSubtitle.setText("SSL certificate info, expiry, SANs\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("VIEW CERT");
        } else if ("REDIRECT".equals(mode)) {
            tvSubtitle.setText("Trace HTTP redirect chain\n" + cfg);
            etTarget.setHint("domain/URL");
            etSni.setHint("Max hops");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("10");
            btnSingle.setText("TRACE");
        } else if ("GEO".equals(mode)) {
            tvSubtitle.setText("IP geolocation: country, city, ISP, ASN\n" + cfg);
            etTarget.setHint("IP address");
            btnSingle.setText("LOCATE");
        } else if ("WHOIS".equals(mode)) {
            tvSubtitle.setText("RDAP domain registration data\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("WHOIS");
        } else if ("CIDR".equals(mode)) {
            tvSubtitle.setText("CIDR -> IPs + PTRs + CDN/hoster range\n" + cfg);
            etTarget.setHint("CIDR / IP / domain or file");
            etSni.setHint("Max IPs (optional)");
            etSni.setVisibility(View.VISIBLE);
            btnSingle.setText("ENUM CIDR");
        }
    }

    private boolean needsTlsPort(String m) {
        return "TLS".equals(m)
                || "SNI".equals(m)
                || "PROXY".equals(m)
                || "DPI".equals(m)
                || "WS".equals(m)
                || "CERT".equals(m);
    }

    private boolean needsWebPort(String m) {
        return needsTlsPort(m)
                || "HEADERS".equals(m)
                || "HTTP_VER".equals(m)
                || "CDN".equals(m)
                || "TECH".equals(m)
                || "ENDPOINT".equals(m)
                || "REDIRECT".equals(m);
    }

    private boolean ensurePortsForCurrentMode() {
        ScanEngine.setPorts(Prefs.getPortsArray(this));

        int[] ports = ScanEngine.activePorts();
        int tlsPort = ScanEngine.firstTlsPort(ports);
        int webPort = ScanEngine.firstWebPort(ports);

        if (needsTlsPort(mode) && tlsPort == 0) {
            toast("Enable a TLS port in Settings (443 or 8443)");
            return false;
        }

        if (needsWebPort(mode) && webPort == 0) {
            toast("Enable a web port in Settings (443, 8443, 80 or 8080)");
            return false;
        }

        return true;
    }

    private void startSingle() {
        if (running) {
            toast("Busy");
            return;
        }

        if (!ensurePortsForCurrentMode()) {
            return;
        }

        w1(false, new Runnable() {
            @Override
            public void run() {
                startSingle2();
            }
        });
    }

    private void startSingle2() {
        final String t = san(etTarget.getText().toString());

        if (t.length() == 0) {
            toast("Enter target");
            return;
        }

        ScanEngine.setPorts(Prefs.getPortsArray(this));

        clearRes();
        beginRun("Scanning " + t + " ...");

        final int to = Prefs.getTimeoutMs(this);
        final String mtd = Prefs.getMethodStr(this);
        final String sni = getSni();

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (G.u(ScannerActivity.this, ScannerActivity.this.mode, false, 0, true) != 0) {
                    finishRun(t, 0, 0);
                    return;
                }

                if ("CIDR".equals(mode)) {
                    runCidrFromTarget(t, to, sni);
                    return;
                }

                if (t.indexOf('/') > 0 && t.matches("\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}")) {
                    List<String> hosts = ScanEngine.expandCidr(t, X.j());

                    if (hosts.isEmpty()) {
                        finishRun(t, 0, 0);
                        return;
                    }

                    addTextLine("[..] CIDR " + t + " -> " + hosts.size() + " hosts", C_INFO);
                    runHostList(hosts, false, to, mtd, sni);
                    return;
                }

                if ("BUGHOST".equals(mode)) {
                    ScanEngine.BugS bs = ScanEngine.bugProbe(t, to, ScanEngine.activePorts());
                    addBugCard(bs);

                    if (bs.sc > 0) {
                        sessionHits.add(t + " score=" + bs.sc + " conf=" + bs.confidence + "%");
                    }

                    finishRun(t, 1, bs.sc > 0 ? 1 : 0);
                } else if ("WS".equals(mode)) {
                    ScanEngine.WsR wr = ScanEngine.ws(t, to);
                    addWsCard(wr, t);
                    finishRun(t, 1, wr.ok ? 1 : 0);
                } else if ("PING".equals(mode)) {
                    int cnt = 4;

                    try {
                        cnt = Integer.parseInt(sni);

                        if (cnt < 1) cnt = 1;
                        if (cnt > 20) cnt = 20;
                    } catch (Exception x) {
                    }

                    int[] pp = ScanEngine.activePorts();

                    if (pp.length == 0) {
                        finishRun(t, 0, 0);
                        return;
                    }

                    ScanEngine.PingR pr = ScanEngine.ping(t, cnt, to, pp[0]);
                    addPingCard(pr, t);
                    finishRun(t, cnt, pr.recv);
                } else if ("CERT".equals(mode)) {
                    ScanEngine.CertR cr = ScanEngine.sslCert(t, to);
                    addCertCard(cr, t);
                    finishRun(t, 1, cr.ok ? 1 : 0);
                } else if ("REDIRECT".equals(mode)) {
                    int hops = 10;

                    try {
                        hops = Integer.parseInt(sni);

                        if (hops < 1) hops = 1;
                        if (hops > 50) hops = 50;
                    } catch (Exception x) {
                    }

                    ScanEngine.RedR rr = ScanEngine.traceRedirect(t, hops, to);
                    addRedirectCard(rr, t);
                    finishRun(t, rr.hops, rr.hops);
                } else if ("GEO".equals(mode)) {
                    ScanEngine.GeoR gr = ScanEngine.geo(t);
                    addGeoCard(gr, t);
                    finishRun(t, 1, gr.ok ? 1 : 0);
                } else if ("WHOIS".equals(mode)) {
                    ScanEngine.WhoR wr = ScanEngine.whois(t);
                    addWhoisCard(wr, t);
                    finishRun(t, 1, wr.ok ? 1 : 0);
                } else if ("ALLOFFLINE".equals(mode) || "ALLONLINE".equals(mode)) {
                    AtomicInteger found = new AtomicInteger(0);
                    scanAllHost(t, mtd, sni, to, found);
                    saveResults();
                    finishRun(t, 1, found.get());
                } else {
                    addResultCard(buildProbeCard(t, sni, to, mtd));

if ("REVIP".equals(mode)) {
    saveResults();
}

finishRun(t, 1, 1);
                }
            }
        }).start();
    }

    private void startFile(final Uri uri) {
        if (running) {
            toast("Busy");
            return;
        }

        if (!ensurePortsForCurrentMode()) {
            return;
        }

        w1(true, new Runnable() {
            @Override
            public void run() {
                startFile2(uri);
            }
        });
    }

    private void startFile2(final Uri uri) {
        clearRes();
        beginRun("Loading file...");
        setStatus("Reading file...", C_INFO);

        ScanEngine.setPorts(Prefs.getPortsArray(this));

        final int to = Prefs.getTimeoutMs(this);
        final String mtd = Prefs.getMethodStr(this);
        final String sni = getSni();

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                BufferedReader br = null;

                final List<String> hosts = new ArrayList<String>();

                try {
                    is = getContentResolver().openInputStream(uri);

                    if (is == null) {
                        finishRun("file", 0, 0);
                        return;
                    }

                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"), 65536);

                    String line;

                    while ((line = br.readLine()) != null) {
                        final String host = san(line);

                        if (host.length() == 0) continue;

                        if (host.indexOf('/') > 0 && host.matches("\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}")) {
                            List<String> ex = ScanEngine.expandCidr(host, X.j() - hosts.size());
                            hosts.addAll(ex);

                            if (hosts.size() >= X.j()) break;
                        } else {
                            hosts.add(host);
                        }
                    }
                } catch (Exception e) {
                    addTextLine("[!] " + shortM(e), C_WARN);
                    finishRun("file", 0, 0);
                    return;
                } finally {
                    try {
                        if (br != null) br.close();
                    } catch (Exception x) {
                    }

                    try {
                        if (is != null) is.close();
                    } catch (Exception x) {
                    }
                }

                if ("CIDR".equals(mode)) {
                    runCidrEnum(hosts, to, "file");
                    return;
                }

                runHostList(hosts, true, to, mtd, sni);
            }
        }).start();
    }

    private void runCidrFromTarget(final String target, final int to, final String sni) {
        int max = X.j();

        try {
            int custom = Integer.parseInt(sni);

            if (custom > 0) {
                max = Math.min(custom, X.j());
            }
        } catch (Exception x) {
        }

        List<String> targets = new ArrayList<String>();

        if (target.indexOf('/') > 0 && target.matches("\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}")) {
            targets.addAll(ScanEngine.expandCidr(target, max));
        } else if (target.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            targets.add(target);
        } else {
            ScanEngine.DnsR d = ScanEngine.dns(target);
            targets.addAll(d.a);
        }

        if (targets.isEmpty()) {
            finishRun(target, 0, 0);
            return;
        }

        addTextLine("[..] CIDR targets: " + targets.size(), C_INFO);

        runCidrEnum(targets, to, target);
    }

    private void runCidrEnum(final List<String> targets, final int to, final String tag) {
        final int total = targets.size();

        if (total == 0) {
            finishRun(tag, 0, 0);
            return;
        }

        totalCount = total;
        setStatus("CIDR enum " + total + " targets...", C_INFO);

        final java.util.Set<String> ipSet =
                java.util.Collections.synchronizedSet(new LinkedHashSet<String>());

        final java.util.Set<String> ptrSet =
                java.util.Collections.synchronizedSet(new LinkedHashSet<String>());

        final java.util.Set<String> ptrIpSet =
                java.util.Collections.synchronizedSet(new LinkedHashSet<String>());

        final List<String> logLines =
                java.util.Collections.synchronizedList(new ArrayList<String>());

        final AtomicInteger done = new AtomicInteger(0);

        int threads = Math.min(G.l(this, Prefs.getThreads(this)), 4);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        final CountDownLatch latch = new CountDownLatch(total);

        for (int i = 0; i < total; i++) {
            final String target = targets.get(i);

            pool.execute(new Runnable() {
                @Override
                public void run() {
                    if (cancelled) {
                        latch.countDown();
                        return;
                    }

                    try {
                        List<String> ips = new ArrayList<String>();

                        if (target.indexOf('/') > 0
                                && target.matches("\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}")) {
                            ips.addAll(ScanEngine.expandCidr(target, Math.min(X.j(), 10000)));
                        } else if (target.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                            ips.add(target);
                        } else {
                            ScanEngine.DnsR d = ScanEngine.dns(target);
                            ips.addAll(d.a);
                        }

                        java.util.Set<String> localPtrs = new LinkedHashSet<String>();
                        java.util.Set<String> localPtrIps = new LinkedHashSet<String>();
                        List<String> localLog = new ArrayList<String>();

                        int ipCount = 0;

                        for (int j = 0; j < ips.size(); j++) {
                            if (cancelled) break;

                            String ip = ips.get(j);

                            if (ip == null || ip.length() == 0) continue;

                            ipSet.add(ip);
                            ipCount++;

                            ScanEngine.PtrR pr = ScanEngine.ptrIntel(ip, Math.min(to, 3000));

                            String ptr = pr.ptr == null ? "" : pr.ptr;

                            if (ptr.length() > 0 && !ptr.equalsIgnoreCase(ip)) {
                                localPtrs.add(ptr);
                                localPtrIps.add(ptr + "|" + ip);
                            }

                            String net = pr.label + " | " +
                                    (pr.org.length() > 0 ? pr.org : pr.isp);

                            localLog.add(
                                    ip + " | " +
                                            (ptr.length() > 0 ? ptr : "no PTR") + " | " +
                                            net + " | " +
                                            pr.asn
                            );
                        }

                        ptrSet.addAll(localPtrs);
                        ptrIpSet.addAll(localPtrIps);
                        logLines.addAll(localLog);

                        addTextLine(
                                "[OK] " + target + " -> " + ipCount + " IPs, " + localPtrs.size() + " PTRs",
                                localPtrs.size() > 0 ? C_OK : C_MUTED
                        );

                        try {
                            Thread.sleep(350);
                        } catch (InterruptedException ix) {
                        }
                    } catch (Throwable x) {
                        addTextLine("[!] " + target + " failed", C_WARN);
                    } finally {
                        tick(done, ptrSet.size(), target, total);
                        latch.countDown();
                    }
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException x) {
        }

        pool.shutdown();

        final String id = Prefs.nextSaveId(this);

        saveFileQuiet("cidr_ips_" + id + ".txt", joinSet(ipSet));
        saveFileQuiet("cidr_ptrs_" + id + ".txt", joinSet(ptrSet));
        saveFileQuiet("cidr_log_" + id + ".txt", joinList(logLines));

        final ArrayList<String> ipList = new ArrayList<String>(ipSet);
        final ArrayList<String> ptrList = new ArrayList<String>(ptrSet);
        final ArrayList<String> ptrIpList = new ArrayList<String>(ptrIpSet);

        final int ipCount = ipList.size();
        final int ptrCount = ptrList.size();

        finishRun(tag, ipCount, ptrCount);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ScannerActivity.this)
                        .setTitle("CIDR enum done")
                        .setMessage(
                                "IPs: " + ipCount +
                                        "\nPTRs: " + ptrCount +
                                        "\n\nSaved:\n" +
                                        "cidr_ips_" + id + ".txt\n" +
                                        "cidr_ptrs_" + id + ".txt\n" +
                                        "cidr_log_" + id + ".txt" +
                                        "\n\nReverse IP which list?"
                        )
                        .setPositiveButton("PTRs", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                runReverseEnum(ptrIpList, true);
                            }
                        })
                        .setNeutralButton("IPs", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                runReverseEnum(ipList, false);
                            }
                        })
                        .setNegativeButton("Done", null)
                        .show();
            }
        });
    }

    private void runReverseEnum(final List<String> items, final boolean ptrMode) {
		if (items == null || items.isEmpty()) {
			toast(ptrMode ? "No PTRs" : "No IPs");
			return;
		}

		clearRes();
		beginRun("Reverse IP " + items.size() + (ptrMode ? " PTRs" : " IPs"));

		final int total = items.size();
		totalCount = total;

		final java.util.Set<String> domains =
            java.util.Collections.synchronizedSet(new LinkedHashSet<String>());

		final List<String> logLines =
            java.util.Collections.synchronizedList(new ArrayList<String>());

		final AtomicInteger done = new AtomicInteger(0);

		new Thread(new Runnable() {
				@Override
				public void run() {
					int threads = Math.min(
						G.l(ScannerActivity.this, Prefs.getThreads(ScannerActivity.this)),
						2
					);

					ExecutorService pool = Executors.newFixedThreadPool(threads);
					final CountDownLatch latch = new CountDownLatch(total);  // <-- made final

					for (int i = 0; i < total; i++) {
						final String item = items.get(i);

						pool.execute(new Runnable() {
								@Override
								public void run() {
									if (cancelled) {
										latch.countDown();
										return;
									}

									try {
										List<String> ips = new ArrayList<String>();

										if (ptrMode) {
											String ptrPart = item;
											String ipPart = null;

											int bar = item.lastIndexOf('|');

											if (bar > 0 && bar < item.length() - 1) {
												ptrPart = item.substring(0, bar).trim();
												ipPart = item.substring(bar + 1).trim();
											}

											if (ipPart != null && ipPart.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
												ips.add(ipPart);
											} else {
												ScanEngine.DnsR d = ScanEngine.dns(ptrPart);
												ips.addAll(d.a);

												if (ips.isEmpty() && ipPart != null) {
													ips.add(ipPart);
												}
											}
										} else {
											if (item.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
												ips.add(item);
											} else {
												ScanEngine.DnsR d = ScanEngine.dns(item);
												ips.addAll(d.a);
											}
										}

										List<String> localDomains = new ArrayList<String>();

										for (int j = 0; j < ips.size(); j++) {
											if (cancelled) break;

											String ip = ips.get(j);

											if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
												continue;
											}

											List<String> rev = ScanEngine.revIp(ip, 10000);

											for (int k = 0; k < rev.size(); k++) {
												String dom = rev.get(k);

												if (dom == null || dom.length() == 0) continue;
												if (dom.startsWith("PTR:")) continue;
												if (dom.indexOf(' ') >= 0) continue;

												if (domains.add(dom)) {
													localDomains.add(dom);
												}
											}

											logLines.add(item + " | " + ip + " | " + localDomains.size() + " domains");
										}

										addTextLine(
											"[OK] " + item + " -> " + localDomains.size() + " domains",
											localDomains.size() > 0 ? C_OK : C_MUTED
										);

										try {
											Thread.sleep(1200);
										} catch (InterruptedException ix) {
										}
									} catch (Throwable x) {
										addTextLine("[!] " + item + " failed", C_WARN);
									} finally {
										tick(done, domains.size(), item, total);
										latch.countDown();
									}
								}
							});
					}

					try {
						latch.await();
					} catch (InterruptedException x) {
					}

					pool.shutdown();

					final String id = Prefs.nextSaveId(ScannerActivity.this);

					saveFileQuiet("reverse_domains_" + id + ".txt", joinSet(domains));
					saveFileQuiet("reverse_log_" + id + ".txt", joinList(logLines));

					final int found = domains.size();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								addHitCard(
									ptrMode ? "PTRs" : "IPs",
									"REVERSE IP DONE",
									found + " unique domains",
									"Saved reverse_domains_" + id + ".txt",
									C_OK
								);
							}
						});

					finishRun("reverse", total, found);
				}
			}).start();
	}

    private void runHostList(final List<String> hosts, final boolean fromFile,
                             final int to, final String mtd, final String sni) {

        final int threads = G.l(this, Prefs.getThreads(this));
        final String mode = this.mode;
        final String tag = fromFile ? "file" : "cidr";

        ScanEngine.setPorts(Prefs.getPortsArray(this));

        final int[] ports = ScanEngine.activePorts().clone();
        final long delayMs = Prefs.getDelayMs(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int total = hosts.size();

                if (total == 0) {
                    finishRun(tag, 0, 0);
                    return;
                }

                if (G.u(ScannerActivity.this, mode, fromFile, total, true) != 0) {
                    finishRun(tag, 0, 0);
                    return;
                }

                totalCount = total;

                setStatus("Scanning " + total + " hosts with " + threads + " threads...", C_INFO);

                final List<ScanEngine.BugS> scores =
                        java.util.Collections.synchronizedList(new ArrayList<ScanEngine.BugS>());

                final AtomicInteger done = new AtomicInteger(0);
                final AtomicInteger bugs = new AtomicInteger(0);

                if ("BUGHOST".equals(mode)) {
                    final CountDownLatch latch = new CountDownLatch(total);
                    ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 8));

                    final List<Runnable> jobs = new ArrayList<Runnable>();

                    for (int i = 0; i < total; i++) {
                        final String host = hosts.get(i);

                        jobs.add(new Runnable() {
                            @Override
                            public void run() {
                                if (cancelled) {
                                    latch.countDown();
                                    return;
                                }

                                try {
                                    BUG_RATE.one();

                                    ScanEngine.BugS bs = ScanEngine.bugProbe(host, to, ports);

                                    scores.add(bs);

                                    if (bs.sc >= 21) bugs.incrementAndGet();

                                    if (bs.sc >= 41) {
                                        sessionHits.add(host + " [BUGHOST " + bs.sc + " " + bs.lv + "]");
                                    }
                                } catch (Exception x) {
                                } finally {
                                    tick(done, bugs.get(), host, total);
                                    latch.countDown();
                                }
                            }
                        });
                    }

                    for (int i = 0; i < jobs.size(); i++) {
                        pool.execute(jobs.get(i));
                    }

                    try {
                        latch.await();
                    } catch (InterruptedException x) {
                    }

                    pool.shutdown();

                    if (!scores.isEmpty()) {
                        java.util.Collections.sort(scores, new Comparator<ScanEngine.BugS>() {
                            @Override
                            public int compare(ScanEngine.BugS a, ScanEngine.BugS b) {
                                return b.sc - a.sc;
                            }
                        });

                        showBugResults(new ArrayList<ScanEngine.BugS>(scores), done.get());
                    }

                    saveHits();
                    finishRun(tag, done.get(), bugs.get());
                    return;
                }

                final int lanes = Math.min(threads, 16);
                ExecutorService pool = Executors.newFixedThreadPool(lanes);

                for (int li = 0; li < lanes; li++) {
                    final int lane = li;

                    pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            int localCount = 0;

                            for (int idx = lane; idx < total; idx += lanes) {
                                if (cancelled) break;

                                HOST_RATE.one();

                                localCount++;

                                final String host = hosts.get(idx);

                                try {
                                    scanHost(host, mtd, sni, to, done, bugs);
                                } catch (Throwable t) {
                                }

                                tick(done, bugs.get(), host, total);

                                if (delayMs > 0) {
                                    try {
                                        Thread.sleep(delayMs);
                                    } catch (InterruptedException x) {
                                    }
                                }

                                if (localCount % 75 == 0) {
                                    try {
                                        Thread.sleep(700);
                                    } catch (InterruptedException x) {
                                    }
                                }
                            }
                        }
                    });
                }

                pool.shutdown();

                try {
                    pool.awaitTermination(7, TimeUnit.DAYS);
                } catch (InterruptedException x) {
                }

                saveHits();
                finishRun(tag, done.get(), bugs.get());
            }
        }).start();
    }

    private void scanHost(final String host, final String mtd, final String sni, final int to,
                          final AtomicInteger done, final AtomicInteger bugs) {
        if (cancelled) return;

        try {
            if ("ALLOFFLINE".equals(mode) || "ALLONLINE".equals(mode)) {
                scanAllHost(host, mtd, sni, to, bugs);
            } else {
                scanOne(host, mode, mtd, sni, to, bugs);
            }
        } catch (Throwable t) {
        }
    }

    private void scanAllHost(final String host, final String mtd, final String sni, final int to,
                             final AtomicInteger bugs) {
        String[] tools = "ALLOFFLINE".equals(mode) ? OFFLINE_TOOLS : ONLINE_TOOLS;

        for (int i = 0; i < tools.length; i++) {
            if (cancelled) break;
            scanOne(host, tools[i], mtd, sni, to, bugs);
        }
    }

    private void scanOne(final String host, final String theMode, final String mtd, final String sni,
                         final int to, final AtomicInteger bugs) {

        int[] ports = ScanEngine.activePorts();
        int tlsPort = ScanEngine.firstTlsPort(ports);
        int webPort = ScanEngine.firstWebPort(ports);

        if (needsTlsPort(theMode) && tlsPort == 0) return;
        if (needsWebPort(theMode) && webPort == 0) return;

        if ("WS".equals(theMode)) {
            ScanEngine.WsR wr = ScanEngine.ws(host, to);

            if (wr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [WS] " + wr.c + " " + wr.st);
                row("GET", String.valueOf(wr.c), "", tlsPort, "", host);
                addHitCard(host, "WS UPGRADE", wr.ms + "ms", wr.c + " " + wr.st, C_OK);
            }
        } else if ("TLS".equals(theMode)) {
            ScanEngine.Result r = ScanEngine.tls(host, to, mtd);

            if (r.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [HTTP " + r.c + "] " + (r.s.length() > 0 ? r.s : r.ip));
                row(mtd, String.valueOf(r.c), r.s, tlsPort, r.ip, host);
                addHitCard(host, "HTTP " + r.c, r.ms + "ms", r.s.length() > 0 ? r.s : r.ip, C_OK);
            }
        } else if ("SNI".equals(theMode)) {
            String fi = (sni == null || sni.length() == 0) ? null : sni;
            ScanEngine.Result r = ScanEngine.sni(host, fi, to, mtd);

            if (r.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [SNI " + r.c + "]");
                row(mtd, String.valueOf(r.c), "", 443, r.ip, host);
                addHitCard(host, "SNI ACCEPTED", r.ms + "ms", "code=" + r.c, C_OK);
            }
        } else if ("PROXY".equals(theMode)) {
            ScanEngine.Result r = ScanEngine.proxy(host, sni, to, mtd);

            if (r.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [PROXY " + r.c + "]");
                row(mtd, String.valueOf(r.c), "", tlsPort, r.ip, host);
                addHitCard(host, "PROXY OK", r.ms + "ms", "code=" + r.c, C_OK);
            }
        } else if ("PORT".equals(theMode)) {
            if (ports.length == 0) return;

            StringBuilder op = new StringBuilder();

            for (int i = 0; i < ports.length; i++) {
                PORT_RATE.one();

                if (ScanEngine.port(host, ports[i], Math.min(1000, to))) {
                    String p = ScanEngine.hostPort(host, ports[i])
                            + " (" + ScanEngine.portService(ports[i]) + ")";

                    op.append(op.length() > 0 ? ", " : "").append(p);
                    row("TCP", "OPEN", ScanEngine.portService(ports[i]), ports[i], "", host);
                }
            }

            if (op.length() > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [ports: " + op + "]");
                addTarget(host);
                addHitCard(host, "PORTS OPEN", "", op.toString(), C_OK);
            }
        } else if ("DPI".equals(theMode)) {
            ScanEngine.DpiR dr = ScanEngine.dpi(host, to);

            if (dr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [DPI] " + dr.r);
                addTarget(host);
                addHitCard(host, "DPI DETECTED", "", dr.r, C_DANGER);
            }
        } else if ("CDN".equals(theMode)) {
            ScanEngine.CdnR cr = ScanEngine.cdn(host, to);

            if (cr.d) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [CDN: " + cr.p + "]");
                addTarget(host);
                addHitCard(host, "CDN FOUND", cr.ms + "ms", cr.p, C_WARN);
            }
        } else if ("HEADERS".equals(theMode)) {
            ScanEngine.SecR sr = ScanEngine.sec(host, to);

            if (sr.score >= 50) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [headers " + sr.score + "%]");
                row(mtd, String.valueOf(sr.c), "", webPort, "", host);
                addHitCard(host, "HEADERS " + sr.score + "%", "",
                        sr.p.size() + "/" + (sr.p.size() + sr.m.size()) + " present", C_OK);
            }
        } else if ("HTTP_VER".equals(theMode)) {
            ScanEngine.HvR hv = ScanEngine.httpVer(host, to);

            if (hv.b1) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [1.1=" + yn(hv.b1) + " 2=" + yn(hv.b2) + " 3=" + yn(hv.b3) + "]");
                row("HTTP", hv.b2 ? "2" : (hv.b1 ? "1.1" : "?"), "", webPort, "", host);
                addHitCard(host, "HTTP VERSIONS", hv.ms + "ms",
                        "1.1=" + yn(hv.b1) + " 2=" + yn(hv.b2) + " 3=" + yn(hv.b3), C_OK);
            }
        } else if ("DNS".equals(theMode)) {
            ScanEngine.DnsR dns = ScanEngine.dns(host);

            if (dns.ok) {
                bugs.incrementAndGet();

                String a = dns.a.isEmpty() ? "" : dns.a.get(0);

                sessionHits.add(host + " [DNS " + a + "]");
                addTarget(host);
                addTarget(a);
                addHitCard(host, "DNS RESOLVED", "", a, C_OK);
            }
        } else if ("PING".equals(theMode)) {
            int cnt = 4;

            try {
                cnt = Integer.parseInt(sni);

                if (cnt < 1) cnt = 1;
                if (cnt > 20) cnt = 20;
            } catch (Exception x) {
            }

            if (ports.length == 0) return;

            ScanEngine.PingR pr = ScanEngine.ping(host, cnt, to, ports[0]);

            if (pr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [ping " + pr.avgMs + "ms port " + ports[0] + "]");
                row("TCP", "PING", "", ports[0], pr.ip, host);
                addHitCard(host, "PING OK", "recv " + pr.recv + "/" + pr.sent,
                        ScanEngine.hostPort(host, ports[0]) + " avg=" + pr.avgMs + "ms", C_OK);
            }
        } else if ("CERT".equals(theMode)) {
            ScanEngine.CertR cr = ScanEngine.sslCert(host, to);

            if (cr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [cert " + cr.daysLeft + "d]");
                addTarget(host);
                addHitCard(host, "CERT VALID", cr.daysLeft < 30 ? "EXPIRING" : "",
                        "expires " + cr.daysLeft + "d", cr.daysLeft < 30 ? C_WARN : C_OK);
            }
        } else if ("REDIRECT".equals(theMode)) {
            int hops = 10;

            try {
                hops = Integer.parseInt(sni);

                if (hops < 1) hops = 1;
                if (hops > 50) hops = 50;
            } catch (Exception x) {
            }

            ScanEngine.RedR rr = ScanEngine.traceRedirect(host, hops, to);

            if (rr.hops > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [redirects " + rr.hops + "]");
                addTarget(host);
                addHitCard(host, "REDIRECTS", rr.hops + " hops", rr.finalUrl, C_OK);
            }
        } else if ("GEO".equals(theMode)) {
            ScanEngine.GeoR gr = ScanEngine.geo(host);

            if (gr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [" + gr.country + ", " + gr.city + "]");
                addTarget(host);
                addHitCard(host, "LOCATED", gr.isp, gr.country + ", " + gr.city, C_OK);
            }
        } else if ("WHOIS".equals(theMode)) {
            ScanEngine.WhoR wr = ScanEngine.whois(host);

            if (wr.ok) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [whois]");
                addTarget(host);
                addHitCard(host, "WHOIS FOUND", "", "registration data", C_OK);
            }
        } else if ("TECH".equals(theMode)) {
            ScanEngine.TechR tr = ScanEngine.tech(host, to);

            if (tr.c > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [tech " + (tr.sv.length() > 0 ? tr.sv : "http " + tr.c) + "]");
                row(mtd, String.valueOf(tr.c), tr.sv, webPort, "", host);
                addHitCard(host, "TECH", tr.p, tr.sv.length() > 0 ? tr.sv : "HTTP " + tr.c, C_OK);
            }
        } else if ("SUBDOMAIN".equals(theMode)) {
            ScanEngine.SubR sub = ScanEngine.deepEnum(host, new ScanEngine.HCB() {
                @Override
                public void st(String m) {
                }

                @Override
                public void pr(String h, int d, int t) {
                }
            });

            if (sub.tu > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [subdomains " + sub.tu + "]");
                addTarget(host);

                java.util.Collections.sort(sub.s);

                List<String> items = new ArrayList<String>();

                for (int i = 0; i < sub.s.size(); i++) {
                    sessionDetails.add(host + " | subdomain | " + sub.s.get(i));
                    addTarget(sub.s.get(i));
                }

                int capS = Math.min(sub.s.size(), 60);

                for (int i = 0; i < capS; i++) {
                    items.add(sub.s.get(i));
                }

                addHitCardList(host, "SUBDOMAINS", sub.tu + " found", items, C_OK);
            }
        } else if ("DEEPENUM".equals(theMode)) {
            ScanEngine.SubR sub = ScanEngine.deepEnum(host, new ScanEngine.HCB() {
                @Override
                public void st(String m) {
                }

                @Override
                public void pr(String h, int d, int t) {
                }
            });

            if (sub.tu > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [deepenum " + sub.tu + "]");
                addTarget(host);

                java.util.Collections.sort(sub.s);

                List<String> items = new ArrayList<String>();

                for (int i = 0; i < sub.s.size(); i++) {
                    sessionDetails.add(host + " | deepenum | " + sub.s.get(i));
                    addTarget(sub.s.get(i));
                }

                int capD = Math.min(sub.s.size(), 60);

                for (int i = 0; i < capD; i++) {
                    items.add(sub.s.get(i));
                }

                addHitCardList(host, "DEEP ENUM", sub.tu + " unique", items, C_OK);
            }
        } else if ("TAKEOVER".equals(theMode)) {
            ScanEngine.TkR tk = ScanEngine.takeover(host, to);

            if (tk.v) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [takeover " + tk.sv + "]");
                sessionDetails.add(host + " | takeover | " + tk.sv + " :: " + tk.dt);
                addTarget(host);
                addHitCard(host, "TAKEOVER RISK", "", tk.sv + " — " + tk.dt, C_DANGER);
            }
        } else if ("WAYBACK".equals(theMode)) {
            int max = 5000;

            try {
                max = Integer.parseInt(sni);
            } catch (Exception x) {
            }

            ScanEngine.WbR wb = ScanEngine.wayback(host, max);

            if (wb.t > 0) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [wayback " + wb.t + "]");
                addTarget(host);

                List<String> items = new ArrayList<String>();

                for (int i = 0; i < wb.iu.size(); i++) {
                    sessionDetails.add(host + " | wayback | " + wb.iu.get(i));
                    addTarget(wb.iu.get(i));
                }

                int capW = Math.min(wb.iu.size(), 40);

                for (int i = 0; i < capW; i++) {
                    items.add(wb.iu.get(i));
                }

                addHitCardList(host, "WAYBACK", wb.in + " juicy", items, C_OK);
            }
        } else if ("ENDPOINT".equals(theMode)) {
            if (webPort == 0) return;

            List<ScanEngine.EpR> res = ScanEngine.fuzzEndpoints(host, webPort, to);
            List<String> hits = new ArrayList<String>();

            for (int i = 0; i < res.size(); i++) {
                ScanEngine.EpR er = res.get(i);

                boolean hit = er.c == 200 || er.c == 301 || er.c == 302
                        || er.c == 401 || er.c == 403 || er.c == 407;

                if (hit) {
                    hits.add(er.url + " -> " + er.c);
                    sessionDetails.add(host + " | endpoint | " + er.url + " -> " + er.c);
                    row("HEAD", String.valueOf(er.c), "", webPort, "", host);
                }
            }

            if (!hits.isEmpty()) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [endpoints " + hits.size() + "]");
                addTarget(host);
                addHitCardList(host, "ENDPOINTS", hits.size() + " exposed", hits, C_WARN);
            }
        } else if ("REVIP".equals(theMode)) {
            List<String> rev = ScanEngine.revIp(host, 10000);

            if (!rev.isEmpty()) {
                bugs.incrementAndGet();
                sessionHits.add(host + " [revip " + rev.size() + "]");
                addTarget(host);

                List<String> items = new ArrayList<String>();

                for (int i = 0; i < rev.size(); i++) {
                    sessionDetails.add(host + " | revip | " + rev.get(i));
                    
                    String rv = rev.get(i);
                    if (rv.startsWith("PTR: ")) rv = rv.substring(5).trim();
                    addTarget(rv);
                }

                int capR = Math.min(rev.size(), 60);

                for (int i = 0; i < capR; i++) {
                    items.add(rev.get(i));
                }

                addHitCardList(host, "REVERSE IP", rev.size() + " entries", items, C_OK);
            }
        }
    }

    private void tick(final AtomicInteger done, final int found, final String current, final int total) {
        int d = done.incrementAndGet();

        if (d > total) d = total;

        if (d % 5 == 0 || d == total) {
            updateProg(d, found, current, total);
        }
    }

    private void saveHits() {
        saveResults();
    }

  private void saveResults() {
    if (sessionRows.isEmpty() && sessionHits.isEmpty() && sessionDetails.isEmpty()) {
        toast("Nothing to save");
        return;
    }

    final String id = Prefs.nextSaveId(this);

    // domains_XX.txt : clean domains first, then clean IPs
    StringBuilder dl = new StringBuilder();

    synchronized (domSet) {
        for (String d : domSet) dl.append(d).append("\n");
    }

    synchronized (ipSet) {
        for (String d : ipSet) dl.append(d).append("\n");
    }

    if (dl.length() == 0) {
        for (int i = 0; i < sessionHits.size(); i++) {
            dl.append(sessionHits.get(i)).append("\n");
        }
    }

    saveFile("domains_" + id + ".txt", dl.toString());

    // log_XX.txt : clean table
    StringBuilder log = new StringBuilder();

    log.append(pad("Method", 8)).append(pad("Code", 6)).append(pad("Server", 16))
            .append(pad("Port", 6)).append(pad("IP", 18)).append("Host\n");

    log.append(pad("------", 8)).append(pad("----", 6)).append(pad("------", 16))
            .append(pad("----", 6)).append(pad("--", 18)).append("----\n");

    synchronized (sessionRows) {
        for (int i = 0; i < sessionRows.size(); i++) {
            Row r = sessionRows.get(i);

            log.append(pad(r.m, 8))
                    .append(pad(r.c, 6))
                    .append(trunc(r.s, 16))
                    .append(pad(r.p, 6))
                    .append(pad(r.ip, 18))
                    .append(r.h)
                    .append("\n");
        }
    }

    if (!sessionDetails.isEmpty()) {
        log.append("\n# === DETAILS ===\n");

        for (int i = 0; i < sessionDetails.size(); i++) {
            log.append(sessionDetails.get(i)).append("\n");
        }
    }

    saveFile("log_" + id + ".txt", log.toString());
}
    private void startDeep() {
        if (running) {
            toast("Busy");
            return;
        }

        w1(false, new Runnable() {
            @Override
            public void run() {
                startDeep2();
            }
        });
    }

    private void startDeep2() {
        final String d = san(etTarget.getText().toString());

        if (d.length() == 0) {
            toast("Enter domain");
            return;
        }

        clearRes();
        beginRun("Deep enum: " + d + " ...");
        addTextLine("Deep enum: " + d + " [4 sources]", C_INFO);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (G.u(ScannerActivity.this, ScannerActivity.this.mode, false, 0, true) != 0) {
                    finishRun(d, 0, 0);
                    return;
                }

                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override
                    public void st(String m) {
                        addTextLine("[..] " + m, C_MUTED);
                    }

                    @Override
                    public void pr(String h, int dn, int t) {
                    }
                };

                ScanEngine.SubR r = ScanEngine.deepEnum(d, cb);

                addTextLine("crt.sh: " + r.crt + "  certspotter: " + r.cs + "  alienvault: " + r.av + "  HT: " + r.ht, C_INFO);
                addTextLine("Total unique: " + r.tu, C_OK);

                for (int i = 0; i < r.s.size(); i++) {
                    addTextLine("  " + r.s.get(i), C_MUTED);
                }

                finishRun(d, r.tu, r.tu);
            }
        }).start();
    }

    private void startTk() {
        if (running) {
            toast("Busy");
            return;
        }

        w1(false, new Runnable() {
            @Override
            public void run() {
                startTk2();
            }
        });
    }

    private void startTk2() {
        final String d = san(etTarget.getText().toString());

        if (d.length() == 0) {
            toast("Enter domain");
            return;
        }

        clearRes();
        beginRun("Takeover check: " + d + " ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (G.u(ScannerActivity.this, ScannerActivity.this.mode, false, 0, true) != 0) {
                    finishRun(d, 0, 0);
                    return;
                }

                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override
                    public void st(String m) {
                        addTextLine("[..] " + m, C_MUTED);
                    }

                    @Override
                    public void pr(String h, int dn, int t) {
                    }
                };

                ScanEngine.SubR r = ScanEngine.deepEnum(d, cb);

                int vuln = 0;

                for (int i = 0; i < r.s.size(); i++) {
                    String h = r.s.get(i);

                    ScanEngine.TkR tk = ScanEngine.takeover(h, Prefs.getTimeoutMs(ScannerActivity.this));

                    if (tk.v) {
                        vuln++;
                        addTextLine("[!!] " + h + " -> " + tk.sv + " TAKEOVER RISK", C_DANGER);
                        sessionHits.add(h + " TAKEOVER " + tk.sv);
                    } else if (tk.sv.length() > 0) {
                        addTextLine("[OK] " + h + " -> " + tk.sv + " (safe)", C_OK);
                    } else {
                        addTextLine("[OK] " + h, C_MUTED);
                    }
                }

                finishRun(d, r.s.size(), vuln);
            }
        }).start();
    }

    private void startEp() {
        if (running) {
            toast("Busy");
            return;
        }

        if (!ensurePortsForCurrentMode()) {
            return;
        }

        w1(false, new Runnable() {
            @Override
            public void run() {
                startEp2();
            }
        });
    }

    private void startEp2() {
        final String d = san(etTarget.getText().toString());

        if (d.length() == 0) {
            toast("Enter domain");
            return;
        }

        ScanEngine.setPorts(Prefs.getPortsArray(this));

        final int epPort = ScanEngine.firstWebPort(ScanEngine.activePorts());

        if (epPort == 0) {
            toast("Enable a web port in Settings first");
            return;
        }

        clearRes();
        beginRun("Endpoint fuzz: " + d + " ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (G.u(ScannerActivity.this, ScannerActivity.this.mode, false, 0, true) != 0) {
                    finishRun(d, 0, 0);
                    return;
                }

                List<ScanEngine.EpR> res = ScanEngine.fuzzEndpoints(d, epPort, Prefs.getTimeoutMs(ScannerActivity.this));

                int found = 0;

                for (int i = 0; i < res.size(); i++) {
                    ScanEngine.EpR er = res.get(i);

                    boolean hit = er.c == 200 || er.c == 301 || er.c == 302
                            || er.c == 401 || er.c == 403 || er.c == 407;

                    if (hit) {
                        found++;
                        addTextLine("[HIT] " + er.url + " -> " + er.c + " " + er.ms + "ms", C_WARN);
                        sessionHits.add(er.url + " " + er.c);
                    } else {
                        addTextLine("[--] " + er.url + " -> " + er.c + " " + er.ms + "ms", C_MUTED);
                    }
                }

                finishRun(d, res.size(), found);
            }
        }).start();
    }

    private void startHosts() {
        if (running) {
            toast("Busy");
            return;
        }

        w1(false, new Runnable() {
            @Override
            public void run() {
                startHosts2();
            }
        });
    }

    private void startHosts2() {
        final String tld = san(etTarget.getText().toString());

        if (tld.length() == 0) {
            toast("Enter TLD");
            return;
        }

        int limit = 100;

        try {
            limit = Integer.parseInt(getSni());

            if (limit < 1) limit = 100;
            if (limit > 5000) limit = 5000;
        } catch (Exception x) {
        }

        clearRes();
        beginRun("Hosts find: ." + tld + " ...");

        final int lim = limit;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (G.u(ScannerActivity.this, ScannerActivity.this.mode, false, 0, true) != 0) {
                    finishRun(tld, 0, 0);
                    return;
                }

                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override
                    public void st(String m) {
                        addTextLine("[..] " + m, C_MUTED);
                    }

                    @Override
                    public void pr(String h, int dn, int t) {
                        addTextLine("[" + dn + "/" + t + "] " + h, C_MUTED);
                    }
                };

                List<String> res = ScanEngine.hostsFind(tld, lim, false, Prefs.getTimeoutMs(ScannerActivity.this), cb);

                for (int i = 0; i < res.size(); i++) {
                    addTextLine(res.get(i), C_MUTED);
                }

                finishRun(tld, res.size(), res.size());
            }
        }).start();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_PICK && res == RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri == null) return;

            if ("SPLIT".equals(mode)) {
                doSplit(uri);
                return;
            }

            startFile(uri);
        }
    }

    private void doSplit(Uri uri) {
        try {
            splitChunk = 25000;

            String custom = etTarget.getText().toString().trim();

            if (custom.length() > 0) {
                try {
                    splitChunk = Integer.parseInt(custom);

                    if (splitChunk < 1) splitChunk = 25000;
                    if (splitChunk > 100000) splitChunk = 100000;
                } catch (Exception x) {
                    splitChunk = 25000;
                }
            }

            InputStream is = getContentResolver().openInputStream(uri);

            if (is == null) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            String line;

            int part = 1;
            int count = 0;

            StringBuilder sb = new StringBuilder();

            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
                count++;

                if (count >= splitChunk) {
                    saveFile("part_" + part + ".txt", sb.toString());

                    part++;
                    count = 0;
                    sb = new StringBuilder();
                }
            }

            br.close();

            if (sb.length() > 0) {
                saveFile("part_" + part + ".txt", sb.toString());
            }

            toast("Split into " + part + " part(s) (" + splitChunk + " lines each)");
        } catch (Exception e) {
            toast("Split error: " + e.getMessage());
        }
    }

    private void addWsCard(final ScanEngine.WsR wr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);
                int mu = C_MUTED;

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(14));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(16), dp(14), dp(16), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(12);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText(wr.ok ? "✓" : "✗");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(18f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(wr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(48);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(14);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(wr.ok ? "WS UPGRADE ACCEPTED" : "WS UPGRADE REFUSED");
                st.setTextColor(wr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                hn.setPadding(0, dp(2), 0, 0);

                info.addView(hn);

                hdr.addView(info);

                TextView tm = new TextView(ScannerActivity.this);
                tm.setText(wr.ms + "ms");
                tm.setTextColor(mu);
                tm.setTextSize(11f);

                hdr.addView(tm);

                card.addView(hdr);

                TextView dt = new TextView(ScannerActivity.this);
                dt.setPadding(0, dp(10), 0, 0);
                dt.setTextColor(mu);
                dt.setTextSize(12f);
                dt.setTypeface(Typeface.MONOSPACE);
                dt.setText("HTTP " + wr.c + "  |  " + wr.st + "\n" + wr.hdr);

                card.addView(dt);

                resultsContainer.addView(card);
            }
        });
    }

    private void addPingCard(final ScanEngine.PingR pr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("@");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(pr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(pr.ok ? "REACHABLE" : "UNREACHABLE");
                st.setTextColor(pr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host + (pr.ip.length() > 0 ? " (" + pr.ip + ")" : ""));
                hn.setTextColor(tx);
                hn.setTextSize(13f);

                info.addView(hn);

                hdr.addView(info);

                card.addView(hdr);

                if (pr.ok) {
                    LinearLayout stats = new LinearLayout(ScannerActivity.this);
                    stats.setOrientation(LinearLayout.HORIZONTAL);
                    stats.setPadding(0, dp(8), 0, 0);

                    stats.addView(pingStat("Sent", String.valueOf(pr.sent)));
                    stats.addView(pingStat("Recv", String.valueOf(pr.recv)));
                    stats.addView(pingStat("Avg", pr.avgMs + "ms"));
                    stats.addView(pingStat("Min", pr.minMs + "ms"));
                    stats.addView(pingStat("Max", pr.maxMs + "ms"));

                    card.addView(stats);
                }

                resultsContainer.addView(card);
            }
        });
    }

    private View pingStat(String label, String value) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);

        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(this));
        g.setCornerRadius(dp(6));

        b.setBackgroundDrawable(g);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );

        blp.rightMargin = dp(4);
        b.setLayoutParams(blp);

        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextColor(C_MUTED);
        lv.setTextSize(9f);

        b.addView(lv);

        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(13f);
        vv.setTypeface(null, Typeface.BOLD);

        b.addView(vv);

        return b;
    }

    private void addCertCard(final ScanEngine.CertR cr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);
                int mu = C_MUTED;

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("K");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                int badgeColor = cr.ok ? (cr.daysLeft < 30 ? C_WARN : C_OK) : C_DANGER;

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(badgeColor);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(cr.ok ? (cr.daysLeft < 30 ? "EXPIRING SOON" : "VALID") : "FAILED");
                st.setTextColor(badgeColor);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);

                info.addView(hn);

                hdr.addView(info);

                card.addView(hdr);

                if (cr.ok) {
                    TextView days = new TextView(ScannerActivity.this);
                    days.setPadding(0, dp(8), 0, 0);
                    days.setTextColor(cr.daysLeft < 30 ? C_WARN : mu);
                    days.setTextSize(13f);
                    days.setTypeface(null, Typeface.BOLD);
                    days.setText("Expires in " + cr.daysLeft + " days");

                    card.addView(days);

                    addCertRow(card, "Subject", cr.subject, mu);
                    addCertRow(card, "Issuer", cr.issuer, mu);
                    addCertRow(card, "Serial", cr.serial, mu);
                    addCertRow(card, "Algorithm", cr.sigAlg, mu);
                    addCertRow(card, "Not Before", cr.notBefore, mu);
                    addCertRow(card, "Not After", cr.notAfter, mu);

                    if (cr.sans.length() > 0) {
                        addCertRow(card, "SANs", cr.sans.substring(0, Math.min(cr.sans.length(), 300)), mu);
                    }
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(cr.e);

                    card.addView(err);
                }

                resultsContainer.addView(card);
            }
        });
    }

    private void addCertRow(LinearLayout parent, String label, String value, int color) {
        TextView tv = new TextView(this);
        tv.setPadding(0, dp(4), 0, 0);
        tv.setTextColor(color);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setText(label + ": " + value);

        parent.addView(tv);
    }

    private void addRedirectCard(final ScanEngine.RedR rr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);
                int mu = C_MUTED;

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("R");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(rr.loop ? C_WARN : C_INFO);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(rr.loop ? "REDIRECT LOOP" : "TRACE COMPLETE");
                st.setTextColor(rr.loop ? C_WARN : C_INFO);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);

                info.addView(hn);

                hdr.addView(info);

                card.addView(hdr);

                TextView hops = new TextView(ScannerActivity.this);
                hops.setPadding(0, dp(8), 0, 0);
                hops.setTextColor(mu);
                hops.setTextSize(12f);
                hops.setText("Hops: " + rr.hops + (rr.loop ? " (LOOP DETECTED)" : "") + "\nFinal: " + rr.finalUrl);

                card.addView(hops);

                for (int i = 0; i < rr.chain.size(); i++) {
                    TextView step = new TextView(ScannerActivity.this);
                    step.setPadding(dp(8), dp(2), 0, dp(2));
                    step.setTextColor(mu);
                    step.setTextSize(11f);
                    step.setTypeface(Typeface.MONOSPACE);
                    step.setText((i + 1) + ". " + rr.chain.get(i));

                    card.addView(step);
                }

                resultsContainer.addView(card);
            }
        });
    }

    private void addGeoCard(final ScanEngine.GeoR gr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("G");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(gr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(gr.ok ? "FOUND" : "FAILED");
                st.setTextColor(gr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);

                info.addView(hn);

                hdr.addView(info);

                card.addView(hdr);

                if (gr.ok) {
                    LinearLayout grid = new LinearLayout(ScannerActivity.this);
                    grid.setOrientation(LinearLayout.VERTICAL);
                    grid.setPadding(0, dp(8), 0, 0);

                    addGeoRow(grid, "Country", gr.country, C_MUTED);
                    addGeoRow(grid, "City", gr.city, C_MUTED);
                    addGeoRow(grid, "ISP", gr.isp, C_MUTED);
                    addGeoRow(grid, "Organization", gr.org, C_MUTED);
                    addGeoRow(grid, "ASN", gr.asn, C_MUTED);
                    addGeoRow(grid, "Latitude", gr.lat, C_MUTED);
                    addGeoRow(grid, "Longitude", gr.lon, C_MUTED);

                    card.addView(grid);
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(gr.e);

                    card.addView(err);
                }

                resultsContainer.addView(card);
            }
        });
    }

    private void addGeoRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView lv = new TextView(this);
        lv.setText(label + ": ");
        lv.setTextColor(color);
        lv.setTextSize(12f);
        lv.setTypeface(null, Typeface.BOLD);

        row.addView(lv);

        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(12f);

        row.addView(vv);

        parent.addView(row);
    }

    private void addWhoisCard(final ScanEngine.WhoR wr, final String host) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this);

                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));

                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("O");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);

                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(wr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);

                badge.setBackgroundDrawable(bgd);

                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);

                TextView st = new TextView(ScannerActivity.this);
                st.setText(wr.ok ? "RETRIEVED" : "FAILED");
                st.setTextColor(wr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);

                info.addView(st);

                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);

                info.addView(hn);

                hdr.addView(info);

                card.addView(hdr);

                if (wr.ok) {
                    ScrollView sv = new ScrollView(ScannerActivity.this);
                    sv.setPadding(0, dp(8), 0, 0);

                    TextView raw = new TextView(ScannerActivity.this);
                    raw.setText(wr.raw);
                    raw.setTextColor(C_MUTED);
                    raw.setTextSize(10f);
                    raw.setTypeface(Typeface.MONOSPACE);

                    sv.addView(raw);

                    card.addView(sv);
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(wr.e);

                    card.addView(err);
                }

                resultsContainer.addView(card);
            }
        });
    }

    private void showBugResults(final List<ScanEngine.BugS> scores, final int total) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultsContainer.removeAllViews();

                if (!scores.isEmpty()) {
                    int bugs = 0;
                    int sum = 0;
                    int top = 0;

                    for (int i = 0; i < scores.size(); i++) {
                        ScanEngine.BugS s = scores.get(i);

                        if (s.sc >= 21) bugs++;

                        sum += s.sc;

                        if (s.sc > top) top = s.sc;
                    }

                    addSummary(total, bugs, scores.size() > 0 ? sum / scores.size() : 0, top);
                }

                int show = Math.min(scores.size(), 80);

                for (int i = 0; i < show; i++) {
                    resultsContainer.addView(buildBugCard(scores.get(i)));
                }

                if (scores.size() > 80) {
                    TextView more = new TextView(ScannerActivity.this);
                    more.setText("...and " + (scores.size() - 80) + " more (saved to Downloads/BugScanner/)");
                    more.setTextColor(C_MUTED);
                    more.setTextSize(12f);
                    more.setPadding(dp(4), dp(8), dp(4), dp(4));

                    resultsContainer.addView(more);
                }
            }
        });
    }

    private void addBugCard(final ScanEngine.BugS s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultsContainer.addView(buildBugCard(s));
            }
        });
    }

    private void addResultCard(final View card) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultsContainer.addView(card);
            }
        });
    }

    private void addTextLine(final String text, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = new TextView(ScannerActivity.this);
                tv.setText(text);
                tv.setTextColor(color);
                tv.setTextSize(11f);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setPadding(dp(4), dp(2), dp(4), dp(2));

                resultsContainer.addView(tv);

                svResults.post(new Runnable() {
                    @Override
                    public void run() {
                        svResults.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void addHitCard(final String host, final String title, final String meta,
                            final String detail, final int accentColor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int tx = Prefs.text(ScannerActivity.this);
                int mu = C_MUTED;

                LinearLayout card = buildCardBase(accentColor);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                hdr.addView(buildBadge(accentColor));

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(10);
                info.setLayoutParams(ilp);

                TextView tTitle = new TextView(ScannerActivity.this);
                tTitle.setText(title);
                tTitle.setTextColor(accentColor);
                tTitle.setTextSize(13f);
                tTitle.setTypeface(null, Typeface.BOLD);

                info.addView(tTitle);

                TextView tHost = new TextView(ScannerActivity.this);
                tHost.setText(host);
                tHost.setTextColor(tx);
                tHost.setTextSize(13f);
                tHost.setTypeface(null, Typeface.BOLD);

                info.addView(tHost);

                hdr.addView(info);

                if (meta != null && meta.length() > 0) {
                    TextView tMeta = new TextView(ScannerActivity.this);
                    tMeta.setText(meta);
                    tMeta.setTextColor(mu);
                    tMeta.setTextSize(10f);

                    hdr.addView(tMeta);
                }

                card.addView(hdr);

                if (detail != null && detail.length() > 0) {
                    TextView tDetail = new TextView(ScannerActivity.this);
                    tDetail.setText(detail);
                    tDetail.setTextColor(mu);
                    tDetail.setTextSize(12f);
                    tDetail.setTypeface(Typeface.MONOSPACE);
                    tDetail.setPadding(0, dp(6), 0, 0);

                    card.addView(tDetail);
                }

                resultsContainer.addView(card);

                svResults.post(new Runnable() {
                    @Override
                    public void run() {
                        svResults.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void addHitCardList(final String host, final String title, final String meta,
                                final List<String> items, final int accentColor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int tx = Prefs.text(ScannerActivity.this);
                int mu = C_MUTED;

                LinearLayout card = buildCardBase(accentColor);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);

                hdr.addView(buildBadge(accentColor));

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

                ilp.leftMargin = dp(10);
                info.setLayoutParams(ilp);

                TextView tTitle = new TextView(ScannerActivity.this);
                tTitle.setText(title);
                tTitle.setTextColor(accentColor);
                tTitle.setTextSize(13f);
                tTitle.setTypeface(null, Typeface.BOLD);

                info.addView(tTitle);

                TextView tHost = new TextView(ScannerActivity.this);
                tHost.setText(host);
                tHost.setTextColor(tx);
                tHost.setTextSize(13f);
                tHost.setTypeface(null, Typeface.BOLD);

                info.addView(tHost);

                hdr.addView(info);

                final TextView toggle = new TextView(ScannerActivity.this);
                toggle.setText("\u25B2");
                toggle.setTextColor(accentColor);
                toggle.setTextSize(14f);
                toggle.setTypeface(null, Typeface.BOLD);
                toggle.setGravity(Gravity.CENTER);
                toggle.setPadding(dp(8), dp(4), dp(8), dp(4));

                hdr.addView(toggle);

                card.addView(hdr);

                if (meta != null && meta.length() > 0) {
                    TextView tMeta = new TextView(ScannerActivity.this);
                    tMeta.setText(meta);
                    tMeta.setTextColor(mu);
                    tMeta.setTextSize(11f);
                    tMeta.setPadding(0, dp(2), 0, dp(4));

                    card.addView(tMeta);
                }

                final LinearLayout itemBox = new LinearLayout(ScannerActivity.this);
                itemBox.setOrientation(LinearLayout.VERTICAL);
                itemBox.setVisibility(View.GONE);
                itemBox.setPadding(dp(4), 0, 0, 0);

                for (int i = 0; i < items.size(); i++) {
                    TextView it = new TextView(ScannerActivity.this);
                    it.setText(items.get(i));
                    it.setTextColor(mu);
                    it.setTextSize(11f);
                    it.setTypeface(Typeface.MONOSPACE);
                    it.setPadding(0, dp(2), 0, dp(2));

                    itemBox.addView(it);
                }

                card.addView(itemBox);

                final boolean[] open = {false};

                toggle.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open[0] = !open[0];

                        toggle.setText(open[0] ? "\u25BC" : "\u25B2");
                        itemBox.setVisibility(open[0] ? View.VISIBLE : View.GONE);
                    }
                });

                resultsContainer.addView(card);

                svResults.post(new Runnable() {
                    @Override
                    public void run() {
                        svResults.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private LinearLayout buildCardBase(int accentColor) {
        int bg = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Prefs.stroke(this));

        card.setBackgroundDrawable(gd);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        return card;
    }

    private TextView buildBadge(int accentColor) {
        TextView badge = new TextView(this);
        badge.setText("\u2713");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(14f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);

        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(accentColor);
        bgd.setShape(GradientDrawable.OVAL);

        badge.setBackgroundDrawable(bgd);

        int bs = dp(34);
        badge.setLayoutParams(new LinearLayout.LayoutParams(bs, bs));

        return badge;
    }

    private void addSummary(int scanned, int bugs, int avg, int top) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, 0, 0, dp(10));

        bar.addView(statBox("Scanned", "" + scanned));
        bar.addView(statBox("Bugs", "" + bugs));
        bar.addView(statBox("Avg", "" + avg));
        bar.addView(statBox("Top", "" + top));

        resultsContainer.addView(bar);
    }

    private View statBox(String label, String value) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);

        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(this));
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Prefs.stroke(this));

        b.setBackgroundDrawable(g);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );

        blp.rightMargin = dp(6);
        b.setLayoutParams(blp);

        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextColor(C_MUTED);
        lv.setTextSize(10f);

        b.addView(lv);

        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(15f);
        vv.setTypeface(null, Typeface.BOLD);

        b.addView(vv);

        return b;
    }

    private View buildBugCard(ScanEngine.BugS s) {
        int sc = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this);
        int tx = Prefs.text(this);
        int mu = C_MUTED;

        int scoreColor;
        String lvl;

        if (s.sc >= 61) {
            scoreColor = C_DANGER;
            lvl = "CRITICAL";
        } else if (s.sc >= 41) {
            scoreColor = C_WARN;
            lvl = "HIGH";
        } else if (s.sc >= 21) {
            scoreColor = 0xFF3B82F6;
            lvl = "MEDIUM";
        } else {
            scoreColor = 0xFF64748B;
            lvl = "LOW";
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(sc);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Prefs.stroke(this));

        card.setBackgroundDrawable(gd);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(s.sc));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(16f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);

        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(scoreColor);
        bgd.setShape(GradientDrawable.OVAL);

        badge.setBackgroundDrawable(bgd);

        int sz = dp(42);
        badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));

        r1.addView(badge);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );

        nlp.leftMargin = dp(10);
        names.setLayoutParams(nlp);

        TextView lvt = new TextView(this);
        lvt.setText(lvl + "  " + s.cfStr());
        lvt.setTextColor(scoreColor);
        lvt.setTextSize(11f);
        lvt.setTypeface(null, Typeface.BOLD);

        names.addView(lvt);

        TextView ht = new TextView(this);
        ht.setText(s.ip.length() > 0 ? s.ip : "unreachable");
        ht.setTextColor(tx);
        ht.setTextSize(14f);
        ht.setTypeface(null, Typeface.BOLD);

        names.addView(ht);

        r1.addView(names);

        TextView mt = new TextView(this);
        mt.setText((s.hc > 0 ? "HTTP " + s.hc : "") + "  " + s.ms + "ms  " + s.confidence + "%");
        mt.setTextColor(mu);
        mt.setTextSize(11f);

        r1.addView(mt);

        card.addView(r1);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(0, dp(6), 0, 0);

        if (s.sv.length() > 0) {
            TextView sv2 = new TextView(this);
            sv2.setText("Server: " + s.sv);
            sv2.setTextColor(mu);
            sv2.setTextSize(12f);

            meta.addView(sv2);
        }

        TextView wf2 = new TextView(this);
        wf2.setText("WAF: " + s.wn);
        wf2.setTextColor(s.wf ? C_WARN : C_OK);
        wf2.setTextSize(12f);

        meta.addView(wf2);

        if (s.ws) {
            TextView ws2 = new TextView(this);
            ws2.setText("WebSocket: UPGRADE ACCEPTED  " + s.wss);
            ws2.setTextColor(C_WARN);
            ws2.setTextSize(12f);

            meta.addView(ws2);
        }

        if (s.tk) {
            TextView tk2 = new TextView(this);
            tk2.setText("TAKEOVER: " + s.tks + "  " + s.si.get(Math.min(s.si.size() - 1, 5)));
            tk2.setTextColor(C_DANGER);
            tk2.setTextSize(12f);
            tk2.setTypeface(null, Typeface.BOLD);

            meta.addView(tk2);
        }

        card.addView(meta);

        if (!s.th.isEmpty()) {
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setPadding(0, dp(6), 0, 0);

            for (int i = 0; i < Math.min(s.th.size(), 8); i++) {
                String tech = s.th.get(i);

                TextView pill = new TextView(this);
                pill.setText(tech);
                pill.setTextSize(10f);
                pill.setTypeface(null, Typeface.BOLD);

                GradientDrawable tbg = new GradientDrawable();
                tbg.setColor(0x333D8BFF);
                tbg.setCornerRadius(dp(4));

                pill.setBackgroundDrawable(tbg);
                pill.setTextColor(C_INFO);
                pill.setPadding(dp(5), dp(3), dp(5), dp(3));

                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                if (i > 0) tlp.leftMargin = dp(4);

                pill.setLayoutParams(tlp);

                tr.addView(pill);
            }

            card.addView(tr);
        }

        if (!s.tg.isEmpty()) {
            LinearLayout tr2 = new LinearLayout(this);
            tr2.setOrientation(LinearLayout.HORIZONTAL);
            tr2.setPadding(0, dp(8), 0, 0);

            for (int i = 0; i < s.tg.size(); i++) {
                String tag = s.tg.get(i);

                boolean bad = tag.contains("NO WAF")
                        || tag.contains("TAKEOVER")
                        || tag.contains("EXPOSED")
                        || tag.contains("WS")
                        || tag.contains("NO HSTS")
                        || tag.contains("HTTP")
                        || tag.contains("RISKY")
                        || tag.contains("PORTS")
                        || tag.contains("ENDPOINT");

                TextView pill = new TextView(this);
                pill.setText(tag);
                pill.setTextSize(10f);
                pill.setTypeface(null, Typeface.BOLD);

                GradientDrawable tbg2 = new GradientDrawable();
                tbg2.setColor(bad ? 0x33FF3B4E : 0x3322C55E);
                tbg2.setCornerRadius(dp(4));

                pill.setBackgroundDrawable(tbg2);
                pill.setTextColor(bad ? C_WARN : C_OK);
                pill.setPadding(dp(5), dp(3), dp(5), dp(3));

                LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                if (i > 0) tlp2.leftMargin = dp(4);

                pill.setLayoutParams(tlp2);

                tr2.addView(pill);
            }

            card.addView(tr2);
        }

        if (!s.op.isEmpty()) {
            TextView pt = new TextView(this);
            pt.setPadding(0, dp(6), 0, 0);
            pt.setText("Ports: " + joinI(s.op));
            pt.setTextColor(mu);
            pt.setTextSize(11f);

            card.addView(pt);
        }

        if (s.ep > 0) {
            TextView ep2 = new TextView(this);
            ep2.setPadding(0, dp(4), 0, 0);
            ep2.setText("Endpoints: " + s.ep + " sensitive paths found");
            ep2.setTextColor(s.ep >= 3 ? C_WARN : C_MUTED);
            ep2.setTextSize(11f);

            card.addView(ep2);
        }

        if (!s.si.isEmpty()) {
            LinearLayout sigs = new LinearLayout(this);
            sigs.setOrientation(LinearLayout.VERTICAL);
            sigs.setPadding(0, dp(8), 0, 0);

            for (int i = 0; i < s.si.size(); i++) {
                boolean isRisk = s.si.get(i).contains("NO")
                        || s.si.get(i).contains("TAKEOVER")
                        || s.si.get(i).contains("Weak")
                        || s.si.get(i).contains("HTTP")
                        || s.si.get(i).contains("Risky")
                        || s.si.get(i).contains("WS")
                        || s.si.get(i).contains("endpoint")
                        || s.si.get(i).contains("unusual")
                        || s.si.get(i).contains("open")
                        || s.si.get(i).contains("PORT")
                        || s.si.get(i).contains("EXPOSED");

                TextView sig = new TextView(this);
                sig.setText((isRisk ? " !! " : "    ") + s.si.get(i));
                sig.setTextColor(isRisk ? C_WARN : mu);
                sig.setTextSize(11f);

                sigs.addView(sig);
            }

            card.addView(sigs);
        }

        return card;
    }

    private View buildProbeCard(String host, String sni, int to, String mtd) {
        int bg = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this);
        int tx = Prefs.text(this);
        int mu = C_MUTED;
        int ac = C_INFO;

        int[] ports = ScanEngine.activePorts();
        int tlsPort = ScanEngine.firstTlsPort(ports);
        int webPort = ScanEngine.firstWebPort(ports);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Prefs.stroke(this));

        card.setBackgroundDrawable(gd);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
        );

        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);

        TextView modeTv = new TextView(this);
        modeTv.setText(mode);
        modeTv.setTextColor(ac);
        modeTv.setTextSize(11f);
        modeTv.setTypeface(null, Typeface.BOLD);

        hdr.addView(modeTv);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        hdr.addView(spacer);

        TextView hostTv = new TextView(this);
        hostTv.setText(host);
        hostTv.setTextColor(tx);
        hostTv.setTextSize(14f);
        hostTv.setTypeface(null, Typeface.BOLD);

        hdr.addView(hostTv);

        card.addView(hdr);

        TextView body = new TextView(this);
        body.setPadding(0, dp(8), 0, 0);
        body.setTextColor(mu);
        body.setTextSize(12f);
        body.setTypeface(Typeface.MONOSPACE);

        if ("TLS".equals(mode)) {
            if (tlsPort == 0) {
                body.setText("[--] No TLS port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.Result r = ScanEngine.tls(host, to, mtd);
                row(mtd, String.valueOf(r.c), r.s, tlsPort, r.ip, host);
                body.setText(r.ok ? "[OK] HTTP " + r.c + "  " + r.s + "  " + r.ip + "  " + r.ms + "ms"
							 : "[--] " + host + "  (" + r.e + ")");
                body.setTextColor(r.ok ? C_OK : C_WARN);
            }
        } else if ("SNI".equals(mode)) {
            if (tlsPort == 0) {
                body.setText("[--] No TLS port configured");
                body.setTextColor(C_WARN);
            } else {
                String fi = (sni == null) ? "" : sni.trim();
                ScanEngine.Result r = ScanEngine.sni(host, fi, to, mtd);
                row(mtd, String.valueOf(r.c), "", 443, r.ip, host);
                body.setText(r.ok ? "[OK] SNI accepted  " + r.ms + "ms  code=" + r.c : "[--] " + r.e);
                body.setTextColor(r.ok ? C_OK : C_WARN);
            }
        } else if ("PROXY".equals(mode)) {
            if (tlsPort == 0) {
                body.setText("[--] No TLS port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.Result r = ScanEngine.proxy(host, sni, to, mtd);
                row(mtd, String.valueOf(r.c), "", tlsPort, r.ip, host);
                body.setText(r.ok ? "[OK] code=" + r.c + "  " + r.ms + "ms" : "[--] " + r.e);
                body.setTextColor(r.ok ? C_OK : C_WARN);
            }
        } else if ("PORT".equals(mode)) {
            StringBuilder op = new StringBuilder();

            for (int i = 0; i < ports.length; i++) {
                PORT_RATE.one();

                if (ScanEngine.port(host, ports[i], Math.min(1000, to))) {
                    op.append(op.length() > 0 ? "\n" : "")
						.append(ScanEngine.hostPort(host, ports[i]) + "  (" + ScanEngine.portService(ports[i]) + ")");
                    row("TCP", "OPEN", ScanEngine.portService(ports[i]), ports[i], "", host);
                }
            }

            addTarget(host);
            body.setText(op.length() > 0 ? "[OK] open:" + op : "[--] no open ports");
            body.setTextColor(op.length() > 0 ? C_OK : C_WARN);
        } else if ("DPI".equals(mode)) {
            if (tlsPort == 0) {
                body.setText("[--] No TLS port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.DpiR d = ScanEngine.dpi(host, to);
                addTarget(host);
                body.setText(d.ok ? "[VULN] " + d.r : "[SAFE] " + d.r);
                body.setTextColor(d.ok ? C_DANGER : C_OK);
            }
        } else if ("CDN".equals(mode)) {
            if (webPort == 0) {
                body.setText("[--] No web port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.CdnR c = ScanEngine.cdn(host, to);
                addTarget(host);
                body.setText(c.d ? "[CDN] " + c.p + "  " + c.ms + "ms" : "[NO CDN] " + c.ms + "ms");
                body.setTextColor(c.d ? C_WARN : C_OK);
            }
        } else if ("HEADERS".equals(mode)) {
            if (webPort == 0) {
                body.setText("[--] No web port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.SecR sr = ScanEngine.sec(host, to);
                row(mtd, String.valueOf(sr.c), "", webPort, "", host);
                body.setText("Score: " + sr.score + "%  Present: " + sr.p.size() + "/" + (sr.p.size() + sr.m.size()) + "  " + sr.ms + "ms");
                body.setTextColor(sr.score < 40 ? C_WARN : sr.score < 70 ? C_WARN : C_OK);
            }
        } else if ("HTTP_VER".equals(mode)) {
            if (webPort == 0) {
                body.setText("[--] No web port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.HvR hv = ScanEngine.httpVer(host, to);
                row("HTTP", hv.b2 ? "2" : (hv.b1 ? "1.1" : "?"), "", webPort, "", host);
                body.setText("HTTP/1.1=" + yn(hv.b1) + "  2=" + yn(hv.b2) + "  3=" + yn(hv.b3) + "  " + hv.ms + "ms");
            }
        } else if ("DNS".equals(mode)) {
            ScanEngine.DnsR dns = ScanEngine.dns(host);

            StringBuilder sb = new StringBuilder();

            if (!dns.a.isEmpty()) sb.append("A: ").append(join(dns.a)).append("\n");
            if (!dns.cn.isEmpty()) sb.append("CNAME: ").append(join(dns.cn)).append("\n");
            if (!dns.mx.isEmpty()) sb.append("MX: ").append(join(dns.mx)).append("\n");
            if (!dns.ns.isEmpty()) sb.append("NS: ").append(join(dns.ns));

            addTarget(host);
            if (!dns.a.isEmpty()) addTarget(dns.a.get(0));

            body.setText(sb.toString());
        } else if ("TECH".equals(mode)) {
            if (webPort == 0) {
                body.setText("[--] No web port configured");
                body.setTextColor(C_WARN);
            } else {
                ScanEngine.TechR tr = ScanEngine.tech(host, to);

                StringBuilder sb = new StringBuilder();

                sb.append("HTTP ").append(tr.c).append("  ").append(tr.ms).append("ms");

                if (tr.sv.length() > 0) sb.append("\nServer: ").append(tr.sv);
                if (tr.p.length() > 0) sb.append("\nPowered: ").append(tr.p);

                if (!tr.t.isEmpty()) sb.append("\nTech: ").append(join(tr.t));

                row(mtd, String.valueOf(tr.c), tr.sv, webPort, "", host);
                body.setText(sb.toString());
            }
        } else if ("TAKEOVER".equals(mode)) {
            ScanEngine.TkR tk = ScanEngine.takeover(host, to);
            addTarget(host);
            body.setText(tk.v ? "[!!] " + tk.dt : "[OK] No takeover risk");
            body.setTextColor(tk.v ? C_DANGER : C_OK);
        } else if ("WAYBACK".equals(mode)) {
            int max = 5000;

            if (sni != null && sni.length() > 0) {
                try {
                    max = Integer.parseInt(sni);
                } catch (Exception x) {
                }
            }

            ScanEngine.WbR wb = ScanEngine.wayback(host, max);

            StringBuilder sb = new StringBuilder();

            sb.append("Total: ").append(wb.t).append("  Juicy: ").append(wb.in);

            for (int i = 0; i < Math.min(wb.iu.size(), 20); i++) {
                sb.append("\n  ").append(wb.iu.get(i));
                addTarget(wb.iu.get(i));
            }

            addTarget(host);
            body.setText(sb.toString());
        } else if ("SUBDOMAIN".equals(mode)) {
            ScanEngine.SubR sub = ScanEngine.deepEnum(host, new ScanEngine.HCB() {
					@Override
					public void st(String m) {
					}

					@Override
					public void pr(String h, int d, int t) {
					}
				});

            addTarget(host);
            for (int i = 0; i < sub.s.size(); i++) {
                addTarget(sub.s.get(i));
            }

            body.setText("Found: " + sub.tu + " subdomains\ncrt.sh: " + sub.crt + "  certspotter: " + sub.cs
						 + "\nalienvault: " + sub.av + "  hackertarget: " + sub.ht);
        } else if ("REVIP".equals(mode)) {
            List<String> rev = ScanEngine.revIp(host, 10000);

            for (int i = 0; i < rev.size(); i++) {
                String rv = rev.get(i);

                if (rv.startsWith("PTR: ")) rv = rv.substring(5).trim();

                if (rv.indexOf(' ') < 0) addTarget(rv);
            }

            addTarget(host);

            body.setText("Found: " + rev.size() + " entries\n" + join(rev));
        }

        card.addView(body);

        return card;
    }

    private void w1(boolean fileScan, Runnable go) {
        int rc = G.u(this, mode, fileScan, 0, false);

        if (rc != 0) {
            String msg;

            if (rc == 1) msg = mode + X.p(40);
            else if (rc == 2) msg = X.o(10);
            else if (rc == 3) msg = X.o(11);
            else msg = X.q(6, 0, 0);

            setStatus(msg, C_WARN);
            w2(msg);

            return;
        }

        G.m(this, fileScan);
        go.run();
    }

    private void w2(String reason) {
        final int accent = Prefs.accent(this);
        final int info = Prefs.info(this);
        final int muted = Prefs.muted(this);
        final int text = Prefs.text(this);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView tvWhy = new TextView(this);
        tvWhy.setText(reason);
        tvWhy.setTextColor(C_WARN);
        tvWhy.setTextSize(13f);
        tvWhy.setPadding(0, 0, 0, dp(10));

        wrap.addView(tvWhy);

        LinearLayout free = new LinearLayout(this);
        free.setOrientation(LinearLayout.VERTICAL);
        free.setBackgroundDrawable(Theme.card(this));
        free.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView f1 = new TextView(this);
        f1.setText(X.p(41));
        f1.setTextColor(muted);
        f1.setTextSize(14f);
        f1.setTypeface(null, Typeface.BOLD);

        free.addView(f1);

        int[] fl = G.i(this);

        TextView f2 = new TextView(this);
        f2.setText(X.q(5, fl[0], fl[1]) + "\n" + X.p(35));
        f2.setTextColor(muted);
        f2.setTextSize(12f);

        free.addView(f2);

        wrap.addView(free);

        LinearLayout prem = new LinearLayout(this);
        prem.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable pg = new GradientDrawable();
        pg.setColor(Prefs.card(this));
        pg.setCornerRadius(dp(12));
        pg.setStroke(dp(2), accent);

        prem.setBackgroundDrawable(pg);
        prem.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        plp.topMargin = dp(10);
        prem.setLayoutParams(plp);

        TextView p1 = new TextView(this);
        p1.setText(X.p(2));
        p1.setTextColor(accent);
        p1.setTextSize(14f);
        p1.setTypeface(null, Typeface.BOLD);

        prem.addView(p1);

        TextView p2 = new TextView(this);
        p2.setText(X.p(36) + "\n" + X.p(37));
        p2.setTextColor(text);
        p2.setTextSize(12f);

        prem.addView(p2);

        wrap.addView(prem);

        LinearLayout tri = new LinearLayout(this);
        tri.setOrientation(LinearLayout.VERTICAL);
        tri.setBackgroundDrawable(Theme.card(this));
        tri.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        tlp.topMargin = dp(10);
        tri.setLayoutParams(tlp);

        TextView t1 = new TextView(this);
        t1.setText(X.p(3));
        t1.setTextColor(info);
        t1.setTextSize(14f);
        t1.setTypeface(null, Typeface.BOLD);

        tri.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(X.p(38));
        t2.setTextColor(text);
        t2.setTextSize(12f);

        tri.addView(t2);

        wrap.addView(tri);

        new AlertDialog.Builder(this)
                .setTitle(X.p(33))
                .setView(wrap)
                .setPositiveButton(X.p(16), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        w3();
                    }
                })
                .setNeutralButton(X.p(17), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        w3();
                    }
                })
                .setNegativeButton(X.p(34), null)
                .show();
    }

    private void w3() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(X.c())));
        } catch (Exception e) {
            toast(X.p(16));
        }
    }

    private final Handler etaHandler = new Handler();

    private final Runnable etaTicker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            if (totalCount == 0) {
                tvEta.setText("elapsed " + formatMs(System.currentTimeMillis() - startTime));
            }

            etaHandler.postDelayed(this, 1000);
        }
    };

    private void beginRun(String status) {
        running = true;
        cancelled = false;
        totalCount = 0;
        startTime = System.currentTimeMillis();

        setStatus(status, C_INFO);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvProgress.setVisibility(View.VISIBLE);
                tvEta.setVisibility(View.VISIBLE);
                progressHorizontal.setProgress(0);
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

                etaHandler.postDelayed(etaTicker, 1000);
            }
        });
    }

    private void finishRun(final String target, final int scanned, final int found) {
        running = false;
        etaHandler.removeCallbacks(etaTicker);

        final long elapsed = System.currentTimeMillis() - startTime;
        final int safeScanned = (totalCount > 0 && scanned > totalCount) ? totalCount : scanned;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvProgress.setVisibility(View.GONE);
                tvEta.setVisibility(View.GONE);
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                progressHorizontal.setProgress(100);

                setStatus("Done: " + safeScanned + " scanned, " + found + " hits  (" + elapsed / 1000 + "s)",
                        found > 0 ? C_OK : C_MUTED);
            }
        });

        if (safeScanned > 0) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < sessionHits.size() && i < 5; i++) {
                sb.append(sessionHits.get(i)).append("\n");
            }

            HistoryStore.add(this, mode, target, safeScanned, found, sb.toString());
        }
    }

    private void updateProg(final int done, final int found, final String current, final int total) {
        final int safeDone = Math.min(done, total);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (total > 0 && safeDone > 0) {
                    tvProgress.setText(safeDone + "/" + total);

                    int pct = (int) ((safeDone * 100L) / total);

                    if (pct > 100) pct = 100;

                    progressHorizontal.setProgress(pct);

                    long elapsed = System.currentTimeMillis() - startTime;
                    long eta = elapsed > 0 ? (long) ((double) elapsed / safeDone * (total - safeDone)) : 0;

                    tvEta.setText(formatMs(eta) + " left");
                } else if (total > 0) {
                    tvProgress.setText("0/" + total);
                } else {
                    tvProgress.setText(safeDone + " done");
                }

                setStatus("Scanned " + safeDone + "/" + total + " ... (" + found + " hits)", C_INFO);
            }
        });
    }

    private void setStatus(final String msg, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(msg);
                tvStatus.setTextColor(color);
            }
        });
    }

    private void clearRes() {
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
            resultsContainer.removeAllViews();
            sessionHits.clear();
            sessionDetails.clear();
            sessionRows.clear();
            domSet.clear();
            ipSet.clear();
            progressHorizontal.setProgress(0);
        }
    });
}

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ScannerActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String san(String s) {
        if (s == null) return "";

        s = s.trim();

        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);

        return s;
    }
    
private void row(String m, String c, String s, int p, String ip, String h) {
    Row r = new Row();
    r.m = m == null ? "" : m;
    r.c = c == null ? "" : c;
    r.s = s == null ? "" : s;
    r.p = p > 0 ? String.valueOf(p) : "";
    r.ip = ip == null ? "" : ip;
    r.h = h == null ? "" : h;

    sessionRows.add(r);

    if (ip != null && ip.length() > 0) addTarget(ip);
    addTarget(h);
}

private void addTarget(String t) {
    if (t == null) return;

    t = t.trim().toLowerCase();

    if (t.length() == 0) return;

    if (t.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
        ipSet.add(t);
    } else if (t.indexOf('.') > 0 && t.indexOf(' ') < 0 && t.indexOf('/') < 0) {
        domSet.add(t);
    }
}

private static String pad(String s, int w) {
    if (s == null) s = "";

    StringBuilder sb = new StringBuilder(s);

    while (sb.length() < w) sb.append(' ');

    return sb.toString();
}

private static String trunc(String s, int w) {
    if (s == null) s = "";

    if (s.length() > w) {
        if (w > 3) s = s.substring(0, w - 3) + "...";
        else s = s.substring(0, w);
    }

    return pad(s, w);
}

    private String getSni() {
        return etSni.getText().toString().trim();
    }

    private String yn(boolean b) {
        return b ? "YES" : "NO";
    }

    private String join(List<String> l) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }

        return sb.toString();
    }

    private String joinI(List<Integer> l) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }

        return sb.toString();
    }

    private String joinSet(java.util.Set<String> set) {
        StringBuilder sb = new StringBuilder();

        for (String s : set) {
            sb.append(s).append("\n");
        }

        return sb.toString();
    }

    private String joinList(List<String> list) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i)).append("\n");
        }

        return sb.toString();
    }

    private String shortM(Exception e) {
        String m = e.getMessage();
        return m != null && m.length() > 60 ? m.substring(0, 60) + "..." : m;
    }

    private String formatMs(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m" + ((ms % 60000) / 1000) + "s";
    }

    private void saveFile(String name, String content) {
        saveFileQuiet(name, content);
        toast("Saved: " + name);
    }

    private void saveFileQuiet(String name, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BugScanner");

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);

                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);

                    if (os != null) {
                        os.write(content.getBytes("UTF-8"));
                        os.close();
                    }
                }
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "BugScanner"
                );

                if (!dir.exists()) dir.mkdirs();

                File f = new File(dir, name);

                FileOutputStream fos = new FileOutputStream(f);
                fos.write(content.getBytes("UTF-8"));
                fos.close();
            }
        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }
}
