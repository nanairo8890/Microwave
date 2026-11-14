package app.microwave.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * One screen: grants the "display over other apps" permission, turns the monitor on and off, and sets
 * its opacity and which parts it shows.
 */
public final class MainActivity extends Activity {

    private TextView status;
    private Button toggle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(24 * d);
        box.setPadding(pad, pad, pad, pad);

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
        addLook(box, d);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF141516);
        scroll.setFitsSystemWindows(true);
        scroll.addView(box);
        setContentView(scroll);

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /** The opacity slider and one switch per part of the monitor; the running monitor follows them live. */
    private void addLook(LinearLayout box, float d) {
        final SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);
        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        final TextView opacity = heading(box, d);
        int alpha = Math.max(20, Math.min(100, prefs.getInt("alpha", 100)));
        opacity.setText(getString(R.string.opacity) + "  " + alpha + " %");
        SeekBar bar = new SeekBar(this);
        bar.setMin(20);
        bar.setMax(100);
        bar.setProgress(alpha);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int value, boolean fromUser) {
                opacity.setText(getString(R.string.opacity) + "  " + value + " %");
                prefs.edit().putInt("alpha", value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
            }
        });
        box.addView(bar, wide);

        heading(box, d).setText(R.string.what_show);
        String[] names = {"GPU", getString(R.string.p_mem), "CPU", "RAM", getString(R.string.p_pwr), "FPS",
                getString(R.string.p_cores), getString(R.string.p_fps_graph), getString(R.string.p_cpu_graph)};
        int show = prefs.getInt("show", OverlayView.ALL);
        for (int i = 0; i < names.length; i++) {
            final int bit = 1 << i;
            CheckBox check = new CheckBox(this);
            check.setText(names[i]);
            check.setTextSize(15);
            check.setTextColor(0xFFF2F4F5);
            check.setChecked((show & bit) != 0);
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton v, boolean checked) {
                    int now = prefs.getInt("show", OverlayView.ALL);
                    now = checked ? now | bit : now & ~bit;
                    if (now == 0) {
                        // the last part stays on: an empty monitor could not be seen or dragged
                        v.setChecked(true);
                        return;
                    }
                    prefs.edit().putInt("show", now).apply();
                }
            });
            box.addView(check, wide);
        }
    }

    private TextView heading(LinearLayout box, float d) {
        TextView t = new TextView(this);
        t.setTextSize(14);
        t.setTextColor(0xFFB4BBC1);
        t.setPadding(0, Math.round(28 * d), 0, Math.round(8 * d));
        box.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText(R.string.need_perm);
            toggle.setText(R.string.grant);
        } else if (OverlayService.running) {
            status.setText(R.string.on_screen);
            toggle.setText(R.string.turn_off);
        } else {
            status.setText(R.string.hint);
            toggle.setText(R.string.turn_on);
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
