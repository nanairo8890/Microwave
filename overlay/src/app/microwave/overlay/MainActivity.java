package app.microwave.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** One screen: grants the "display over other apps" permission and turns the monitor on and off. */
public final class MainActivity extends Activity {

    private TextView status;
    private Button toggle;
    private boolean es;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        es = Locale.getDefault().getLanguage().equals("es");
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = Math.round(24 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(0xFF141516);

        TextView title = new TextView(this);
        title.setText("Microwave Overlay");
        title.setTextSize(22);
        title.setTextColor(0xFFF2F4F5);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFFB4BBC1);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Math.round(12 * d), 0, Math.round(20 * d));
        box.addView(status);

        toggle = new Button(this);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle();
            }
        });
        box.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(box);

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText(es
                    ? "Falta el permiso para mostrarse sobre otras apps"
                    : "The permission to display over other apps is missing");
            toggle.setText(es ? "Dar permiso" : "Grant permission");
        } else if (OverlayService.running) {
            status.setText(es
                    ? "El monitor está en pantalla. Arrástralo para moverlo"
                    : "The monitor is on screen. Drag it to move it");
            toggle.setText(es ? "Apagar" : "Turn off");
        } else {
            status.setText(es
                    ? "Muestra FPS, CPU, GPU, memoria y temperatura sobre los juegos. Necesita root en el manager"
                    : "Shows FPS, CPU, GPU, memory and temperature over games. It needs root in the manager");
            toggle.setText(es ? "Encender" : "Turn on");
        }
    }

    private void onToggle() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (OverlayService.running) {
            stopService(service);
            OverlayService.running = false;
        } else {
            startForegroundService(service);
            OverlayService.running = true;
        }
        refresh();
    }
}
