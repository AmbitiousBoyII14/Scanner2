package myscanne.com;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class Prefs {

    private static final String F = "ui_prefs";

    private static final String K_T = "theme";
    private static final String K_FNT = "font";
    private static final String K_D = "density";
    private static final String K_M = "menu";
    private static final String K_TO = "to";
    private static final String K_P = "ports";
    private static final String K_MTD = "mtd";
    private static final String K_TH = "th";
    private static final String K_UA = "ua";
    private static final String K_DELAY = "delay";

    public static final String[] TN = {
            "Crimson Dark",
            "Ocean Dark",
            "Emerald Dark",
            "Amber Dark",
            "Violet Dark",
            "AMOLED",
            "B&W Light",
            "Purple Light",
            "Daylight",
            "Solarized Dark",
            "Matrix Green",
            "Rose Gold",
            "Midnight Blue",
            "Nord Light"
    };

    private static final int[][] TC = {
            {0xFF0D0F14, 0xFF161A22, 0xFF232A36, 0xFFFF3B4E, 0xFF3D8BFF, 0xFFF2F5FA, 0xFF8A93A6, 0},
            {0xFF0A121F, 0xFF12203A, 0xFF1E3350, 0xFF3D8BFF, 0xFF22C55E, 0xFFEAF2FF, 0xFF7E8DA6, 0},
            {0xFF0A1410, 0xFF11241C, 0xFF1E3A2E, 0xFF22C55E, 0xFF3D8BFF, 0xFFEAFBF1, 0xFF7F9C8B, 0},
            {0xFF14100A, 0xFF241E11, 0xFF3A311E, 0xFFF5C518, 0xFFFF3B4E, 0xFFFBF6EA, 0xFFA69A7E, 0},
            {0xFF0F0B18, 0xFF1B142B, 0xFF2C2244, 0xFF8B5CF6, 0xFF22C55E, 0xFFF3EEFF, 0xFF9488AE, 0},
            {0xFF000000, 0xFF0A0A0A, 0xFF1C1C1C, 0xFFFF3B4E, 0xFF3D8BFF, 0xFFFFFFFF, 0xFF8A8A8A, 0},
            {0xFFFFFFFF, 0xFFF5F5F5, 0xFFE0E0E0, 0xFF111111, 0xFF555555, 0xFF111111, 0xFF888888, 1},
            {0xFFFAFAFF, 0xFFFFFFFF, 0xFFE0DCF0, 0xFF7C3AED, 0xFFA78BFA, 0xFF1E1B4B, 0xFF8B7FAD, 1},
            {0xFFF2F5FA, 0xFFFFFFFF, 0xFFD8DEE9, 0xFFE11D48, 0xFF2563EB, 0xFF0D0F14, 0xFF64748B, 1},
            {0xFF002B36, 0xFF073642, 0xFF586E75, 0xFFB58900, 0xFF268BD2, 0xFFEEE8D5, 0xFF839496, 0},
            {0xFF0A0F0A, 0xFF0F1A0F, 0xFF1C2E1C, 0xFF00FF41, 0xFF00C853, 0xFFD9FFD9, 0xFF3E7A3E, 0},
            {0xFF180D14, 0xFF241218, 0xFF3A1F2A, 0xFFE0A3B8, 0xFFF5C518, 0xFFFBEFF3, 0xFFA98293, 0},
            {0xFF0B1120, 0xFF111A33, 0xFF1E2A4F, 0xFF38BDF8, 0xFF6366F1, 0xFFE6EEFF, 0xFF7E8DB0, 0},
            {0xFFECEFF4, 0xFFFFFFFF, 0xFFD8DEE9, 0xFF5E81AC, 0xFF81A1C1, 0xFF2E3440, 0xFF7A8494, 1},
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    public static void setTheme(Context c, int i) {
        sp(c).edit().putInt(K_T, i).apply();
    }

    public static void setFont(Context c, int i) {
        sp(c).edit().putInt(K_FNT, i).apply();
    }

    public static void setDensity(Context c, int i) {
        sp(c).edit().putInt(K_D, i).apply();
    }

    public static void setMenu(Context c, int i) {
        sp(c).edit().putInt(K_M, i).apply();
    }

    public static int theme(Context c) {
        return cI(sp(c).getInt(K_T, 0), TN.length);
    }

    public static int font(Context c) {
        return cI(sp(c).getInt(K_FNT, 0), 2);
    }

    public static int density(Context c) {
        return cI(sp(c).getInt(K_D, 0), 2);
    }

    public static int menu(Context c) {
        return cI(sp(c).getInt(K_M, 0), 2);
    }

    public static void setTimeoutMs(Context c, int ms) {
        sp(c).edit().putInt(K_TO, ms).apply();
    }

    public static int getTimeoutMs(Context c) {
        return sp(c).getInt(K_TO, 1000);
    }

    public static void setPorts(Context c, String p) {
        sp(c).edit().putString(K_P, p).apply();
    }

    public static String getPorts(Context c) {
        return sp(c).getString(K_P, "443");
    }

    public static int[] getPortsArray(Context c) {
        List<Integer> l = new ArrayList<Integer>();

        for (String p : getPorts(c).split(",")) {
            try {
                int n = Integer.parseInt(p.trim());

                if (n > 0 && n <= 65535) {
                    l.add(n);
                }
            } catch (Exception x) {
            }
        }

        if (l.isEmpty()) {
            l.add(443);
        }

        int[] a = new int[l.size()];

        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }

        return a;
    }

    public static void setMethod(Context c, int m) {
        sp(c).edit().putInt(K_MTD, m).apply();
    }

    public static int getMethod(Context c) {
        return cI(sp(c).getInt(K_MTD, 0), 2);
    }

    public static String getMethodStr(Context c) {
        return getMethod(c) == 1 ? "HEAD" : "GET";
    }

    public static void setThreads(Context c, int t) {
        sp(c).edit().putInt(K_TH, t).apply();
    }

    public static int getThreads(Context c) {
        int v = sp(c).getInt(K_TH, 50);

        if (v < 1) v = 1;
        if (v > 500) v = 500;

        return v;
    }

    public static void setUserAgent(Context c, String ua) {
        sp(c).edit().putString(K_UA, ua).apply();
    }

    public static String getUserAgent(Context c) {
        return sp(c).getString(
                K_UA,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        );
    }

    public static void setDelayMs(Context c, int ms) {
        sp(c).edit().putInt(K_DELAY, ms).apply();
    }

    public static int getDelayMs(Context c) {
        return sp(c).getInt(K_DELAY, 0);
    }

    private static final String K_TUT = "tutorial";

    public static boolean seenTutorial(Context c) {
        return sp(c).getBoolean(K_TUT, false);
    }

    public static void markTutorialSeen(Context c) {
        sp(c).edit().putBoolean(K_TUT, true).apply();
    }

    private static final String K_SC = "savecounter";

    public static String nextSaveId(Context c) {
        int n = sp(c).getInt(K_SC, 0) + 1;

        sp(c).edit().putInt(K_SC, n).apply();

        return String.format(java.util.Locale.US, "%02d", n % 100);
    }

    public static int bg(Context c) {
        return TC[theme(c)][0];
    }

    public static int card(Context c) {
        return TC[theme(c)][1];
    }

    public static int stroke(Context c) {
        return TC[theme(c)][2];
    }

    public static int accent(Context c) {
        return TC[theme(c)][3];
    }

    public static int info(Context c) {
        return TC[theme(c)][4];
    }

    public static int text(Context c) {
        return TC[theme(c)][5];
    }

    public static int muted(Context c) {
        return TC[theme(c)][6];
    }

    public static boolean isLight(Context c) {
        return TC[theme(c)][7] == 1;
    }

    public static boolean isDark(int ti) {
        return TC[cI(ti, TC.length)][7] == 0;
    }

    public static boolean isDarkTheme(Context c) {
        return isDark(theme(c));
    }

    public static String themeKind(int ti) {
        ti = cI(ti, TC.length);

        return (TC[ti][7] == 0 ? "Dark" : "Light");
    }

    public static String themeVersion(int ti) {
        ti = cI(ti, TC.length);

        return ti < 9 ? "v1" : "v2";
    }

    public static String themeInfo(Context c) {
        int t = theme(c);

        return TN[t] + " (" + themeKind(t) + " theme)";
    }

    public static int[] palette(int ti) {
        return TC[cI(ti, TC.length)];
    }

    public static boolean isSans(Context c) {
        return font(c) == 1;
    }

    public static boolean isCompact(Context c) {
        return density(c) == 1;
    }

    public static boolean isGrid(Context c) {
        return menu(c) == 1;
    }

    private static int cI(int v, int max) {
        if (v < 0) return 0;
        if (v >= max) return max - 1;
        return v;
    }
}