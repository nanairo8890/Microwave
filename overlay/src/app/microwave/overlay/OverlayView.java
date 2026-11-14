package app.microwave.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/** The monitor itself: readouts, one bar per core and a graph of the last minute. */
final class OverlayView extends View {

    private static final int HISTORY = 60;
    private static final int CYAN = 0xFF22D3EE, VIOLET = 0xFFA855F7, TEXT = 0xFFF2F4F5, DIM = 0xFF9AA2A9;

    private final float d;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path path = new Path();

    private final float[] fpsHist = new float[HISTORY];
    private final float[] cpuHist = new float[HISTORY];
    private int filled;
    private Stats.Sample s;
    private String notice;

    OverlayView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xCC0B0C0D);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(d);
        edge.setColor(0x6622D3EE);
        label.setColor(DIM);
        label.setTextSize(10 * d);
        label.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        value.setColor(TEXT);
        value.setTextSize(11.5f * d);
        value.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        big.setColor(CYAN);
        big.setTextSize(22 * d);
        big.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * d);
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

    void showNotice(String text) {
        notice = text;
        invalidate();
    }

    private boolean hasGpuMem() {
        return s != null && s.gpuMemKb > 0;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // rows: FPS, CPU, core bars, GPU, RAM, (GPU memory), temperature, graph
        float h = 8 + 26 + 16 + 24 + 16 + 16 + (hasGpuMem() ? 16 : 0) + 16 + 40 + 8;
        if (notice != null) h = 8 + 16 * 3 + 8;
        setMeasuredDimension(Math.round(172 * d), Math.round(h * d));
    }

    private static String pct(float v) {
        return v < 0 ? "--" : String.format(Locale.US, "%3.0f%%", v);
    }

    private static String deg(float v) {
        return v < 0 ? "--" : String.format(Locale.US, "%.0f°", v);
    }

    private void row(Canvas c, float y, String name, String text) {
        c.drawText(name, 8 * d, y, label);
        c.drawText(text, 44 * d, y, value);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        box.set(d / 2, d / 2, w - d / 2, h - d / 2);
        c.drawRoundRect(box, 4 * d, 4 * d, bg);
        c.drawRoundRect(box, 4 * d, 4 * d, edge);
        if (notice != null) {
            float y = 22 * d;
            for (String part : notice.split("\n")) {
                c.drawText(part, 8 * d, y, value);
                y += 16 * d;
            }
            return;
        }
        if (s == null) {
            c.drawText("...", 8 * d, 22 * d, value);
            return;
        }
        float y = 30 * d;
        c.drawText("FPS", 8 * d, y, label);
        c.drawText(s.fps < 0 ? "--" : String.format(Locale.US, "%.0f", s.fps), 44 * d, y, big);

        y += 18 * d;
        int top = Math.max(s.freqMhz[2], Math.max(s.freqMhz[1], s.freqMhz[0]));
        row(c, y, "CPU", pct(s.cpu) + (top > 0 ? " " + top + "MHz" : ""));

        // one bar per core: 0-3 little, 4-6 big, 7 prime
        float bw = (w - 16 * d - 7 * 3 * d) / 8, bh = 18 * d, by = y + 6 * d;
        for (int i = 0; i < 8; i++) {
            float x = 8 * d + i * (bw + 3 * d);
            bar.setColor(0x33FFFFFF);
            c.drawRect(x, by, x + bw, by + bh, bar);
            float u = s.core[i];
            if (u > 0) {
                bar.setColor(i == 7 ? VIOLET : CYAN);
                c.drawRect(x, by + bh * (1 - u / 100f), x + bw, by + bh, bar);
            }
        }
        y = by + bh + 14 * d;
        row(c, y, "GPU", pct(s.gpu) + (s.gpuMhz > 0 ? " " + s.gpuMhz + "MHz" : ""));
        y += 16 * d;
        row(c, y, "RAM", s.memTotalKb > 0 && s.memAvailKb >= 0
                ? String.format(Locale.US, "%.1f/%.1f GB", (s.memTotalKb - s.memAvailKb) / 1048576f, s.memTotalKb / 1048576f)
                : "--");
        if (hasGpuMem()) {
            y += 16 * d;
            row(c, y, "VRAM", String.format(Locale.US, "%d MB", s.gpuMemKb / 1024));
        }
        y += 16 * d;
        row(c, y, "TEMP", deg(s.tCpu) + " cpu " + deg(s.tBody));

        // graph of the last minute: FPS in cyan (0 to 120), CPU use in violet (0 to 100 %)
        float gt = y + 8 * d, gb = h - 8 * d, gl = 8 * d, gr = w - 8 * d;
        bar.setColor(0x22FFFFFF);
        c.drawRect(gl, gt, gr, gb, bar);
        graph(c, cpuHist, 100, VIOLET, gl, gt, gr, gb);
        graph(c, fpsHist, 120, CYAN, gl, gt, gr, gb);
    }

    private void graph(Canvas c, float[] data, float max, int color, float l, float t, float r, float b) {
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
        line.setColor(color);
        c.drawPath(path, line);
    }
}
