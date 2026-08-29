package myscanne.com;

public class X {

    static {
        System.loadLibrary("spro");
    }

    public static native String addDevice(String k, String devHash);

    public static native String a();

    public static native String b();

    public static native String c();

    public static native boolean d(String in);

    public static native boolean e(byte[] cert);

    public static native int f(String flat, String key, String dev, String banned, long now, long[] out);

    public static native boolean g(long untilMs, long now);

    public static native int h(int singleUsed, int fileUsed, boolean fileScan);

    public static native void i(int singleUsed, int fileUsed, int[] out);

    public static native int j();

    public static native boolean k(String mode);

    public static native int l(int requested, boolean premium);

    public static native int m(String key, String devHash, long now, long[] out);

    public static native String n();

    public static native String o(int code);

    public static native String p(int id);

    public static native String q(int id, int a, int b);

    public static native String r(long untilMs, long now, boolean premium);

    public static native int u(String mode, int hasKey, long until, long now,
                               int singleUsed, int fileUsed, int fileScan,
                               int total, int post);
}