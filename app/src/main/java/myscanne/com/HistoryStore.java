package myscanne.com;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryStore {

    private static final String FILE_NAME = "scanner_history.json";
    private static final int MAX_ENTRIES = 200;

    public static class Entry {
        public long id;
        public String type;
        public String target;
        public String date;
        public int total;
        public int found;
        public String results;
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    private static JSONArray readArray(Context ctx) {
        File f = file(ctx);

        if (!f.exists()) {
            return new JSONArray();
        }

        FileInputStream fis = null;

        try {
            fis = new FileInputStream(f);

            byte[] data = new byte[(int) f.length()];

            int n = fis.read(data);

            if (n <= 0) {
                return new JSONArray();
            }

            return new JSONArray(new String(data, "UTF-8"));
        } catch (Exception e) {
            return new JSONArray();
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (Exception x) {
            }
        }
    }

    private static void saveArray(Context ctx, JSONArray arr) {
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(file(ctx));

            fos.write(arr.toString().getBytes("UTF-8"));
        } catch (Exception e) {
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Exception x) {
            }
        }
    }

    public static synchronized void add(Context ctx,
                                        String type,
                                        String target,
                                        int total,
                                        int found,
                                        String results) {
        JSONArray arr = readArray(ctx);

        try {
            JSONObject o = new JSONObject();

            long id = System.currentTimeMillis();

            o.put("id", id);
            o.put("type", type);
            o.put("target", target);
            o.put(
                    "date",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(id))
            );
            o.put("total", total);
            o.put("found", found);
            o.put("results", results);

            JSONArray out = new JSONArray();

            out.put(o);

            for (int i = 0; i < arr.length() && i < MAX_ENTRIES - 1; i++) {
                out.put(arr.get(i));
            }

            saveArray(ctx, out);
        } catch (Exception ignored) {
        }
    }

    public static List<Entry> getAll(Context ctx) {
        JSONArray arr = readArray(ctx);

        List<Entry> list = new ArrayList<Entry>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);

            if (o == null) continue;

            Entry e = new Entry();

            e.id = o.optLong("id");
            e.type = o.optString("type", "");
            e.target = o.optString("target", "");
            e.date = o.optString("date", "");
            e.total = o.optInt("total", 0);
            e.found = o.optInt("found", 0);
            e.results = o.optString("results", "");

            list.add(e);
        }

        return list;
    }

    public static synchronized void clear(Context ctx) {
        File f = file(ctx);

        if (f.exists()) {
            f.delete();
        }
    }

    public static int totalScans(Context ctx) {
        return readArray(ctx).length();
    }

    public static int totalHits(Context ctx) {
        JSONArray arr = readArray(ctx);

        int sum = 0;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);

            if (o != null) {
                sum += o.optInt("found", 0);
            }
        }

        return sum;
    }
}