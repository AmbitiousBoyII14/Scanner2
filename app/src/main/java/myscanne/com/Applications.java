package myscanne.com;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class Applications extends Application {

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    @Override
    public void onCreate() {
        this.uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Intent intent = new Intent(getApplicationContext(), DebugActivity.class);

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                );

                intent.putExtra("error", getStackTrace(ex));

                int flags = PendingIntent.FLAG_ONE_SHOT;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        getApplicationContext(),
                        11111,
                        intent,
                        flags
                );

                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

                if (am != null) {
                    am.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            1000,
                            pendingIntent
                    );
                }

                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(2);
            }
        });

        super.onCreate();
    }

    private String getStackTrace(Throwable th) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        Throwable cause = th;

        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }

        final String stacktraceAsString = result.toString();

        printWriter.close();

        return stacktraceAsString;
    }
}