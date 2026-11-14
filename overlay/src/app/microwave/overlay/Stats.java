package app.microwave.overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Samples the hardware once a second through one root shell that stays open: the files it reads
 * (/proc/stat, the kgsl and thermal nodes, the display driver's frame counter) are not readable by
 * a normal app. Each tick writes one command to the shell and reads its answer up to "@E".
 */
final class Stats implements Runnable {

    interface Listener {
        void onSample(Sample s);

        /** The root shell could not be opened or died: root was not granted to this app. */
        void onNoRoot();
    }

    static final class Sample {
        float fps = -1;                 // frames SurfaceFlinger showed per second, -1 if unknown
        float cpu = -1;                 // all cores, percent
        float[] core = new float[8];    // each core, percent; -1 if offline
        int[] freqMhz = {-1, -1, -1};   // little, big and prime clusters
        int gpu = -1;                   // GPU busy, percent
        int gpuMhz = -1;
        long memTotalKb = -1, memAvailKb = -1;
        long gpuMemKb = -1;             // memory the GPU driver has allocated
        float tCpu = -1, tBody = -1;    // degrees C
    }

    private static final String INIT =
            "Z=; for z in /sys/class/thermal/thermal_zone*; do t=; read t < $z/type; "
            + "case $t in cpu-*|cpuss-*) Z=\"$Z $z/temp\";; esac; done 2>/dev/null\n"
            + "K=/sys/class/kgsl/kgsl-3d0; P=/sys/devices/system/cpu/cpufreq\n";

    private static final String TICK =
            "echo @C; head -n 9 /proc/stat 2>/dev/null; "
            + "echo @F; cat $P/policy0/scaling_cur_freq $P/policy4/scaling_cur_freq $P/policy7/scaling_cur_freq 2>/dev/null; "
            + "echo @G; cat $K/gpu_busy_percentage $K/gpuclk 2>/dev/null; "
            + "echo @M; head -n 3 /proc/meminfo 2>/dev/null; "
            + "echo @T; cat $Z 2>/dev/null; "
            + "echo @B; cat /sys/class/thermal/thermal_message/board_sensor_temp 2>/dev/null; "
            + "echo @S; cat /sys/class/drm/sde-crtc-0/measured_fps 2>/dev/null; "
            + "echo @V; cat /sys/class/kgsl/kgsl/page_alloc 2>/dev/null; "
            + "echo @E\n";

    // "fps: 59.8 duration:1000000 frame_count:60": the frames the display committed since the last read
    private static final Pattern FPS = Pattern.compile("fps:\\s*([0-9.]+)");

    private final Listener listener;
    private volatile boolean running = true;
    private Process shell;

    // previous /proc/stat counters: [0] all cores, [1..8] each core
    private final long[] prevTotal = new long[9];
    private final long[] prevIdle = new long[9];
    private boolean first = true; // the first reading spans an unknown time

    Stats(Listener listener) {
        this.listener = listener;
    }

    void stop() {
        running = false;
        Process p = shell;
        if (p != null) p.destroy();
    }

    @Override
    public void run() {
        try {
            shell = new ProcessBuilder("su").redirectErrorStream(true).start();
            Writer out = new OutputStreamWriter(shell.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            out.write(INIT);
            while (running) {
                long started = System.nanoTime();
                out.write(TICK);
                out.flush();
                Sample s = new Sample();
                for (int i = 0; i < s.core.length; i++) s.core[i] = -1;
                char section = 0;
                int row = 0;
                float hottest = -1;
                String line;
                boolean ended = false;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("@E")) {
                        ended = true;
                        break;
                    }
                    if (line.length() == 2 && line.charAt(0) == '@') {
                        section = line.charAt(1);
                        row = 0;
                        continue;
                    }
                    if (line.isEmpty()) continue;
                    switch (section) {
                        case 'C': cpuLine(s, line); break;
                        case 'F': if (row < 3) s.freqMhz[row] = (int) (num(line) / 1000); break;
                        case 'G':
                            if (row == 0) s.gpu = (int) num(line);
                            else if (row == 1) s.gpuMhz = (int) (num(line) / 1000000);
                            break;
                        case 'M':
                            if (line.startsWith("MemTotal:")) s.memTotalKb = num(line.substring(9));
                            else if (line.startsWith("MemAvailable:")) s.memAvailKb = num(line.substring(13));
                            break;
                        case 'T': {
                            float t = num(line) / 1000f;
                            if (t > hottest && t < 200) hottest = t;
                            break;
                        }
                        case 'B': {
                            long v = num(line);
                            s.tBody = v > 1000 ? v / 1000f : v > 200 ? v / 10f : v;
                            break;
                        }
                        case 'S': frames(s, line); break;
                        case 'V': if (row == 0) s.gpuMemKb = num(line) / 1024; break;
                        default: break;
                    }
                    row++;
                }
                if (!ended) break; // the shell closed
                s.tCpu = hottest;
                listener.onSample(s);
                long spent = (System.nanoTime() - started) / 1000000;
                if (spent < 1000) Thread.sleep(1000 - spent);
            }
        } catch (Exception e) {
            // falls through: no shell, or it was destroyed by stop()
        }
        if (running) listener.onNoRoot();
        Process p = shell;
        if (p != null) p.destroy();
    }

    /** The integer a line starts with (after spaces), or -1. */
    private static long num(String s) {
        s = s.trim();
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0) return -1;
        try {
            return Long.parseLong(s.substring(0, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** One line of /proc/stat: "cpu" (all cores) or "cpuN", then user nice system idle iowait ... */
    private void cpuLine(Sample s, String line) {
        if (!line.startsWith("cpu")) return;
        String[] f = line.split("\\s+");
        if (f.length < 6) return;
        int idx;
        if (f[0].equals("cpu")) idx = 0;
        else {
            long n = num(f[0].substring(3));
            if (n < 0 || n > 7) return;
            idx = (int) n + 1;
        }
        long total = 0;
        for (int i = 1; i < f.length && i <= 8; i++) total += Math.max(0, num(f[i]));
        long idle = Math.max(0, num(f[4])) + Math.max(0, num(f[5]));
        long dt = total - prevTotal[idx], di = idle - prevIdle[idx];
        float use = prevTotal[idx] > 0 && dt > 0 ? 100f * (dt - di) / dt : -1;
        if (use > 100) use = 100;
        prevTotal[idx] = total;
        prevIdle[idx] = idle;
        if (idx == 0) s.cpu = use;
        else s.core[idx - 1] = use < 0 ? 0 : use;
    }

    /** The display driver's own measurement (Qualcomm sde): it counts from one read to the next. */
    private void frames(Sample s, String line) {
        Matcher m = FPS.matcher(line);
        if (!m.find()) return;
        try {
            float fps = Float.parseFloat(m.group(1));
            if (!first) s.fps = fps;
            first = false;
        } catch (NumberFormatException e) {
            // leave it unknown
        }
    }
}
