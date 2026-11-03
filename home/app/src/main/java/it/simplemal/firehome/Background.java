package it.simplemal.firehome;

import android.os.Process;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Work the Home must not feel, always below the interface's priority. */
final class Background {
    private static final ThreadFactory LOW = job -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        job.run();
    }, "salotto-background");
    private static final ExecutorService WAITING = Executors.newCachedThreadPool(LOW);
    private static final ExecutorService DRAWING = Executors.newSingleThreadExecutor(LOW);
    /** Its own line, so that it never waits behind a page's worth of drawing: the picture someone is looking at. */
    private static final ExecutorService SHARPENING = Executors.newSingleThreadExecutor(LOW);

    private Background() {}

    /** For what mostly waits, on the network or on a service. */
    static void run(Runnable job) { WAITING.execute(job); }

    /** For what uses the processor: one job at a time, so that four weak cores keep one free. */
    static void draw(Runnable job) { DRAWING.execute(job); }

    /** For reading again, sharper, what is already on screen: one job at a time beside the drawing. */
    static void sharpen(Runnable job) { SHARPENING.execute(job); }
}
