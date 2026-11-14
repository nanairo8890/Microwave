package app.microwave.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;

/** Keeps the monitor on screen over other apps. Drag it to move it; its place is remembered. */
public final class OverlayService extends Service implements Stats.Listener {

    static final String ACTION_STOP = "app.microwave.overlay.STOP";
    static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private OverlayView view;
    private WindowManager.LayoutParams lp;
    private Stats stats;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        foreground();
        if (!Settings.canDrawOverlays(this)) {
            // nothing can be drawn yet: open the app, which asks for the permission
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            stopSelf();
            return START_NOT_STICKY;
        }
        if (view == null) attach();
        return START_STICKY;
    }

    private void foreground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("overlay", "Overlay", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        boolean es = Locale.getDefault().getLanguage().equals("es");
        Notification n = new Notification.Builder(this, "overlay")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Microwave Overlay")
                .setContentText(es ? "Toca para cerrarlo" : "Tap to close it")
                .setContentIntent(stop)
                .setOngoing(true)
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private void attach() {
        wm = getSystemService(WindowManager.class);
        view = new OverlayView(this);
        final SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("x", 24);
        lp.y = prefs.getInt("y", 160);
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = startX + Math.round(e.getRawX() - downX);
                        lp.y = startY + Math.round(e.getRawY() - downY);
                        wm.updateViewLayout(view, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        return true;
                    default:
                        return false;
                }
            }
        });
        wm.addView(view, lp);
        running = true;
        stats = new Stats(this);
        new Thread(stats, "stats").start();
    }

    @Override
    public void onSample(final Stats.Sample s) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (view == null) return;
                boolean hadMem = view.getHeight() > 0;
                view.show(s);
                if (hadMem) view.requestLayout();
            }
        });
    }

    @Override
    public void onNoRoot() {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (view == null) return;
                boolean es = Locale.getDefault().getLanguage().equals("es");
                view.showNotice(es
                        ? "Sin root\nDale root a Microwave\nOverlay en el manager"
                        : "No root\nGrant root to Microwave\nOverlay in the manager");
                view.requestLayout();
            }
        });
    }

    @Override
    public void onDestroy() {
        running = false;
        if (stats != null) stats.stop();
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (RuntimeException ignored) {
                // already gone
            }
            view = null;
        }
        super.onDestroy();
    }
}
