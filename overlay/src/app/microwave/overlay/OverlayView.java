package app.microwave.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * The monitor itself, laid out like a PC on-screen display: a colored label per row, large values
 * with small units, one bar per core and two graphs of the last minute. No panel behind it; the
 * text carries its own dark outline so it reads over any game.
 */
final class OverlayView extends View {

    private static final int HISTORY = 60;
    private static final int GREEN = 0xFF35C24A, BLUE = 0xFF2F9BFF, ROSE = 0xFFD98A8A;
    private static final int ORANGE = 0xFFFF9A1F, WHITE = 0xFFFFFFFF, YELLOW = 0xFFE8E04A;

    static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;

    private final float density;
    private float scale = 1;
    private float d; // one dp at the current scale: every size below is a multiple of it
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private final float[] fpsHist = new float[HISTORY];
    private final float[] cpuHist = new float[HISTORY];
    private int filled;
    private Stats.Sample s;
    private String notice;

    /** What the monitor draws; the app's switches set these. */
    static final int GPU = 1, MEM = 2, CPU = 4, RAM = 8, PWR = 16, FPS = 32, CORES = 64, FPS_GRAPH = 128, CPU_GRAPH = 256;
    static final int ALL = 511;
    private int shown = ALL;

    OverlayView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        // A wide, squared face like the one PC on-screen displays use (bundled, SIL OFL); the
        // system's condensed bold if it can not be loaded
        Typeface face;
        try {
            face = Typeface.createFromAsset(c.getAssets(), "michroma.ttf");
        } catch (RuntimeException e) {
            face = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        }
        value.setTextAlign(Paint.Align.RIGHT);
        small.setColor(YELLOW);
        for (Paint p : new Paint[]{label, value, unit, small}) {
            p.setTypeface(face);
            p.setFakeBoldText(true);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        line.setStyle(Paint.Style.STROKE);
        line.setColor(WHITE);
        box.setColor(0xCC0B0C0D);
        setScale(1);
    }

    float getScale() {
        return scale;
    }

    /** Resizes the whole monitor (pinch): 1 is the normal size. */
    void setScale(float to) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, to));
        d = density * scale;
        label.setTextSize(13 * d);
        value.setTextSize(13 * d);
        unit.setTextSize(7 * d);
        small.setTextSize(8.5f * d);
        line.setStrokeWidth(1.5f * d);
        line.setShadowLayer(2 * d, 0, 0, 0xFF000000);
        requestLayout();
        invalidate();
    }

    void show(Stats.Sample sample) {
        s = sample;
        notice = null;
        System.arraycopy(fpsHist, 1, fpsHist, 0, HISTORY - 1);
        System.arraycopy(cpuHist, 1, cpuHist, 0, HISTORY - 1);
        fpsHist[HISTORY - 1] = sample.fps;
        cpuHist[HISTORY - 1] = sample.cpu;
        if (filled < HISTORY) filled++;
        invalidate();
    }

    void setShown(int mask) {
        shown = mask & ALL;
        requestLayout();
        invalidate();
    }

    private boolean on(int part) {
        return (shown & part) != 0;
    }

    /** Where the rows end, in dp: the core bars and the graphs go under it. */
    private float rowsEnd() {
        int rows = Integer.bitCount(shown & (GPU | MEM | CPU | RAM | PWR | FPS));
        return rows > 0 ? 22 + (rows - 1) * 23 + 7 : 4;
    }

    void showNotice(String text) {
        notice = text;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // the rows, the core bars and the graphs with their captions, whichever are on
        float h = rowsEnd() + (on(CORES) ? 14 : 0) + (on(FPS_GRAPH) ? 50 : 0) + (on(CPU_GRAPH) ? 50 : 0) + 8;
        if (notice != null) h = 8 + 16 * 3 + 8;
        setMeasuredDimension(Math.round((notice != null ? 190 : 292) * d), Math.round(h * d));
    }

    /** Text with a black outline under it and a slightly heavier stroke, so it reads over a game. */
    private void text(Canvas c, String t, float x, float y, Paint p) {
        int color = p.getColor();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * d);
        p.setColor(0xFF000000);
        c.drawText(t, x, y, p);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(0.6f * d);
        p.setColor(color);
        c.drawText(t, x, y, p);
    }

    private static String num(float v) {
        return v < 0 ? "--" : String.format(Locale.US, "%.0f", v);
    }

    /** One value of a row: the number right-aligned in its column, the unit raised beside it. */
    private void cell(Canvas c, int col, float y, String number, String u, int color) {
        float right = (56 + 50 + col * 78) * d;
        value.setColor(color);
        unit.setColor(color);
        text(c, number, right, y, value);
        text(c, u, right + 3 * d, y - 8 * d, unit);
    }

    private void name(Canvas c, float y, String text, int color) {
        label.setColor(color);
        text(c, text, 4 * d, y, label);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (notice != null) {
            rect.set(0, 0, w, h);
            c.drawRoundRect(rect, 4 * d, 4 * d, box);
            small.setColor(WHITE);
            float ny = 22 * d;
            for (String part : notice.split("\n")) {
                text(c, part, 8 * d, ny, small);
                ny += 16 * d;
            }
            small.setColor(YELLOW);
            return;
        }
        if (s == null) return;
        float y = 22 * d, step = 23 * d;

        if (on(GPU)) {
            name(c, y, "GPU", GREEN);
            cell(c, 0, y, num(s.tGpu), "°C", ORANGE);
            cell(c, 1, y, num(s.gpu), "%", ORANGE);
            cell(c, 2, y, num(s.gpuMhz), "MHz", ORANGE);
            y += step;
        }
        if (on(MEM)) {
            name(c, y, "MEM", GREEN);
            cell(c, 0, y, s.gpuMemKb > 0 ? String.valueOf(s.gpuMemKb / 1024) : "--", "MB", ORANGE);
            y += step;
        }
        if (on(CPU)) {
            int top = Math.max(s.freqMhz[2], Math.max(s.freqMhz[1], s.freqMhz[0]));
            name(c, y, "CPU", BLUE);
            cell(c, 0, y, num(s.cpu), "%", ORANGE);
            cell(c, 1, y, num(top), "MHz", ORANGE);
            cell(c, 2, y, num(s.tCpu), "°C", ORANGE);
            y += step;
        }
        if (on(RAM)) {
            name(c, y, "RAM", BLUE);
            cell(c, 0, y, s.memTotalKb > 0 && s.memAvailKb >= 0 ? String.valueOf((s.memTotalKb - s.memAvailKb) / 1024) : "--", "MB", ORANGE);
            y += step;
        }
        if (on(PWR)) {
            // what the battery is giving (or taking, while it charges), and the body temperature
            name(c, y, "PWR", YELLOW);
            cell(c, 0, y, s.watts < 0 ? "--" : String.format(Locale.US, "%.1f", s.watts), "W", ORANGE);
            cell(c, 2, y, num(s.tBody), "°C", ORANGE);
            y += step;
        }
        if (on(FPS)) {
            name(c, y, "FPS", ROSE);
            cell(c, 0, y, num(s.fps), "FPS", WHITE);
            cell(c, 1, y, s.fps > 0 ? String.format(Locale.US, "%.1f", 1000f / s.fps) : "--", "ms", WHITE);
        }

        float gy = rowsEnd() * d;
        if (on(CORES)) {
            // one bar per core: 0-3 little, 4-6 big, 7 prime
            float bh = 10 * d, bw = (w - 8 * d - 7 * 3 * d) / 8;
            for (int i = 0; i < 8; i++) {
                float x = 4 * d + i * (bw + 3 * d);
                fill.setColor(0x66808080);
                c.drawRect(x, gy, x + bw, gy + bh, fill);
                float u = s.core[i];
                if (u > 0) {
                    fill.setColor(i == 7 ? ORANGE : i >= 4 ? BLUE : GREEN);
                    c.drawRect(x, gy + bh * (1 - u / 100f), x + bw, gy + bh, fill);
                }
            }
            gy += 14 * d;
        }
        if (on(FPS_GRAPH)) {
            graph(c, "Framerate", num(s.fps) + " FPS", fpsHist, 120, gy, w);
            gy += 50 * d;
        }
        if (on(CPU_GRAPH)) graph(c, "CPU usage", num(s.cpu) + " %", cpuHist, 100, gy, w);
    }

    /** A captioned graph of the last minute, 50 dp tall in all. */
    private void graph(Canvas c, String caption, String now, float[] data, float max, float top, float w) {
        text(c, caption, 4 * d, top + 11 * d, small);
        small.setTextAlign(Paint.Align.RIGHT);
        text(c, now, w - 4 * d, top + 11 * d, small);
        small.setTextAlign(Paint.Align.LEFT);
        float t = top + 15 * d, b = top + 47 * d, l = 4 * d, r = w - 4 * d;
        fill.setColor(0x40000000);
        c.drawRect(l, t, r, b, fill);
        path.rewind();
        boolean started = false;
        for (int i = HISTORY - filled; i < HISTORY; i++) {
            float v = data[i];
            if (v < 0) continue;
            float x = l + (r - l) * i / (HISTORY - 1);
            float y = b - (b - t) * Math.min(v, max) / max;
            if (started) path.lineTo(x, y);
            else {
                path.moveTo(x, y);
                started = true;
            }
        }
        c.drawPath(path, line);
    }
}
