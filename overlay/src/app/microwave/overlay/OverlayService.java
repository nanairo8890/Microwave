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


/** Keeps the monitor on screen over other apps. Drag it to move it; its place is remembered. */
public final class OverlayService extends Service implements Stats.Listener {

    static final String ACTION_STOP = "app.microwave.overlay.STOP";
    static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private OverlayView view;
    private WindowManager.LayoutParams lp;
    private Stats stats;
    private SharedPreferences prefs;
    // The app's opacity slider and switches reach the monitor through the preferences (same process)
    private final SharedPreferences.OnSharedPreferenceChangeListener onPrefs =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    if ("alpha".equals(key) || "show".equals(key)) applyLook();
                }
            };

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
        Notification n = new Notification.Builder(this, "overlay")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Microwave Overlay")
                .setContentText(getString(R.string.notif))
                .setContentIntent(stop)
                .setOngoing(true)
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private void attach() {
        wm = getSystemService(WindowManager.class);
        view = new OverlayView(this);
        prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("x", 24);
        lp.y = prefs.getInt("y", 160);
        view.setScale(prefs.getFloat("scale", 1));
        applyLook();
        prefs.registerOnSharedPreferenceChangeListener(onPrefs);
        // One finger drags it; two fingers pinch it smaller or larger, like a photo
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean pinching;
            private float pinchDist, pinchScale;

            private float spread(MotionEvent e) {
                return (float) Math.hypot(e.getRawX(0) - e.getRawX(1), e.getRawY(0) - e.getRawY(1));
            }

            private void anchor(float x, float y) {
                downX = x;
                downY = y;
                startX = lp.x;
                startY = lp.y;
            }

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pinching = false;
                        anchor(e.getRawX(), e.getRawY());
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (e.getPointerCount() == 2) {
                            pinching = true;
                            pinchDist = Math.max(1, spread(e));
                            pinchScale = view.getScale();
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (pinching && e.getPointerCount() >= 2) {
                            view.setScale(pinchScale * spread(e) / pinchDist);
                        } else if (!pinching) {
                            lp.x = startX + Math.round(e.getRawX() - downX);
                            lp.y = startY + Math.round(e.getRawY() - downY);
                            wm.updateViewLayout(view, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        // the pinch is over; the finger that stays does not drag until it is lifted
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        pinching = false;
                        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).putFloat("scale", view.getScale()).apply();
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

    private void applyLook() {
        if (view == null) return;
        view.setAlpha(Math.max(20, Math.min(100, prefs.getInt("alpha", 100))) / 100f);
        view.setShown(prefs.getInt("show", OverlayView.ALL));
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
                view.showNotice(getString(R.string.noroot));
                view.requestLayout();
            }
        });
    }

    @Override
    public void onDestroy() {
        running = false;
        if (stats != null) stats.stop();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(onPrefs);
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
