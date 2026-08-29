package myscanne.com;

public class NativeScan {

    static {
        System.loadLibrary("spro");
    }

    public static native String resolve(String host);

    public static native boolean tcpOpen(String host, int port, int timeoutMs);

    public static native int tcpPing(String host, int port, int count, int timeoutMs, long[] out);

    public static native int httpCode(String host, String path, int timeoutMs);
}