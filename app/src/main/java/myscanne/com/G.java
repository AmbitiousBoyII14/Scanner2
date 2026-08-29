package myscanne.com;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

public class G {

    private static final String P0 = "cf";

    private static final String A = "a";
    private static final String B = "b";
    private static final String C = "c";
    private static final String D = "d";
    private static final String E = "e";
    private static final String F = "f";

    private static SharedPreferences sp(Context c) {
        SharedPreferences p = c.getSharedPreferences(P0, Context.MODE_PRIVATE);
        syncIn(c, p);
        return p;
    }

    private static boolean synced = false;

    private static File markerFile(Context c) {
        String h = r(c);

        return new File(
                c.getFilesDir(),
                ".cf_" + h.substring(0, Math.min(8, h.length()))
        );
    }

    private static byte[] xorb(byte[] data, String key) {
        byte[] out = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key.charAt(i % key.length()));
        }

        return out;
    }

    private static synchronized void syncIn(Context c, SharedPreferences p) {
        if (synced) return;

        synced = true;

        try {
            File f = markerFile(c);

            if (!f.exists()) return;

            FileInputStream in = new FileInputStream(f);

            byte[] buf = new byte[(int) f.length()];

            int n = in.read(buf);

            in.close();

            if (n <= 0) return;

            String s = new String(xorb(buf, r(c)), "UTF-8");

            String[] parts = s.split(",");

            if (parts.length < 2) return;

            int ms = Integer.parseInt(parts[0]);
            int mf = Integer.parseInt(parts[1]);

            int cs = p.getInt(E, 0);
            int cf = p.getInt(F, 0);

            if (ms > cs || mf > cf) {
                p.edit()
                        .putInt(E, Math.max(ms, cs))
                        .putInt(F, Math.max(mf, cf))
                        .apply();
            }
        } catch (Exception e) {
        }
    }

    private static void syncOut(Context c, SharedPreferences p) {
        try {
            File f = markerFile(c);

            String s = p.getInt(E, 0) + "," + p.getInt(F, 0);

            FileOutputStream out = new FileOutputStream(f);

            out.write(xorb(s.getBytes("UTF-8"), r(c)));

            out.close();
        } catch (Exception e) {
        }
    }

    public static boolean a(Context c) {
        SharedPreferences p = sp(c);

        if (p.getString(A, "").length() == 0) return false;

        return X.g(p.getLong(C, 0), System.currentTimeMillis());
    }

    public static boolean b(Context c) {
        return a(c) && "t".equals(sp(c).getString(B, ""));
    }

    public static String c(Context c) {
        return sp(c).getString(A, "");
    }

    public static long d(Context c) {
        if (!a(c)) return 0;

        long u = sp(c).getLong(C, 0);

        if (u == 0) return -1;

        return Math.max(0, u - System.currentTimeMillis());
    }

    public static String e(Context c) {
        long u = a(c) ? sp(c).getLong(C, 0) : -1;

        return X.r(u, System.currentTimeMillis(), a(c));
    }

    public static void f(Context c, String k, String t, long u) {
        sp(c).edit()
                .putString(A, k)
                .putString(B, t)
                .putLong(C, u)
                .remove(D)
                .apply();
    }

    public static void g(Context c) {
        sp(c).edit().putBoolean(D, true).apply();
    }

    public static void h(Context c) {
        sp(c).edit().clear().apply();
    }

    public static int[] i(Context c) {
        SharedPreferences p = sp(c);

        int[] out = new int[2];

        X.i(p.getInt(E, 0), p.getInt(F, 0), out);

        return out;
    }

    public static String j(Context c, boolean fs) {
        if (a(c)) return null;

        SharedPreferences p = sp(c);

        int bl = X.h(p.getInt(E, 0), p.getInt(F, 0), fs);

        if (bl == 2) return X.o(11);
        if (bl == 1) return X.o(10);

        return null;
    }

    public static boolean k(Context c, String m) {
        return !a(c) && X.k(m);
    }

    public static int l(Context c, int r) {
        return X.l(r, a(c));
    }

    public static int u(Context c, String m, boolean fs, int total, boolean post) {
        SharedPreferences p = sp(c);

        int hk = p.getString(A, "").length() > 0 ? 1 : 0;

        return X.u(
                m,
                hk,
                p.getLong(C, 0),
                System.currentTimeMillis(),
                p.getInt(E, 0),
                p.getInt(F, 0),
                fs ? 1 : 0,
                total,
                post ? 1 : 0
        );
    }

    public static void m(Context c, boolean fs) {
        if (a(c)) return;

        SharedPreferences p = sp(c);

        if (fs) {
            p.edit().putInt(F, p.getInt(F, 0) + 1).apply();
        } else {
            p.edit().putInt(E, p.getInt(E, 0) + 1).apply();
        }

        syncOut(c, p);
    }

    public static class R {
        public int x;
        public String y = "p";
        public long z;
    }

    public static R n(Context c, String k) {
        R r = new R();

        long[] res = new long[2];

        String dev = r(c);

        r.x = X.m(k, dev, System.currentTimeMillis(), res);
        r.z = res[0];
        r.y = res[1] == 1 ? "t" : "p";

        if (r.x == 0) {
            String ar = X.addDevice(k, dev);

            if ("LIMIT".equals(ar)) {
                r.x = 3;
                return r;
            }

            if ("OK".equals(ar)) {
                long[] res2 = new long[2];

                int rc2 = X.m(k, dev, System.currentTimeMillis(), res2);

                if (rc2 == 0) {
                    r.z = res2[0];
                    r.y = res2[1] == 1 ? "t" : "p";
                }
            }
        }

        return r;
    }

    public static boolean o(Context c) {
        String k = c(c);

        if (k.length() == 0) return false;
        if (!a(c)) return false;
        if ("t".equals(sp(c).getString(B, ""))) return true;

        R r = n(c, k);

        if (r.x == 6) return true;

        if (r.x == 0) {
            String devHash = r(c);

            String ar = X.addDevice(k, devHash);

            if ("LIMIT".equals(ar)) return false;

            if ("OK".equals(ar)) {
                long[] res2 = new long[2];

                int rc2 = X.m(k, devHash, System.currentTimeMillis(), res2);

                if (rc2 == 0) {
                    r.z = res2[0];
                    r.y = res2[1] == 1 ? "t" : "p";
                }
            }

            G.f(c, k, r.y, r.z);

            return true;
        }

        return false;
    }

    public static String p(int code) {
        return X.o(code);
    }

    public static boolean q(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(
                    c.getPackageName(),
                    PackageManager.GET_SIGNATURES
            );

            Signature[] sigs = pi.signatures;

            if (sigs == null || sigs.length == 0) return false;

            return X.e(sigs[0].toByteArray());
        } catch (Exception e) {
            return false;
        }
    }

    public static String r(Context c) {
        try {
            String data = Settings.Secure.getString(
                    c.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );

            if (data == null) data = "unknown";

            data += android.os.Build.BOARD
                    + android.os.Build.BRAND
                    + android.os.Build.DEVICE;

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] hash = md.digest(data.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < Math.min(16, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }

            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}