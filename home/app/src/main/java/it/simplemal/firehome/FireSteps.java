package it.simplemal.firehome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Selects entries on Fire OS's own pages, under a mask of TaskService that lets keys through. These
 * pages answer only to the real OK key, not to clicks or touches, so the person presses it: no adb,
 * and no question from Android about it. Each step lists the words of an entry in the known languages.
 */
final class FireSteps {
    private static final long POLL_MS = 120;
    private static final long FIND_LIMIT_MS = 6000;
    private static final long PRESS_LIMIT_MS = 30000;
    private static final long SETTLE_MS = 700;
    private static final long FOCUS_WAIT_MS = 1000;
    private static final long LEAVE_MS = 300;
    private static final int MOST_BACKS = 4;

    private final AccessibilityService service;
    private final Handler main;
    private final Runnable look = this::look;
    private String[][] steps;
    private IntConsumer selected;
    private Consumer<Boolean> done;
    private AccessibilityNodeInfo entry;
    private int windowId;
    private volatile boolean okPressed;
    private int step;
    private long limit;
    private long focusBy;
    private Runnable choose;
    private boolean announced;
    private Runnable lost;
    private boolean ended;

    FireSteps(AccessibilityService service, Handler main) {
        this.service = service;
        this.main = main;
    }

    /** `selected` hears each step as its entry gets ready for OK; `done` hears false if it never was. */
    void run(Intent page, String[][] steps, IntConsumer selected, Consumer<Boolean> done) {
        this.steps = steps;
        this.selected = selected;
        this.done = done;
        limit = SystemClock.elapsedRealtime() + FIND_LIMIT_MS;
        try {
            service.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main.postDelayed(look, POLL_MS);
        } catch (ActivityNotFoundException e) {
            main.post(() -> end(false));
        }
    }

    void cancel() { end(false); }

    /** Once: a press can arrive while the next look is on its way. */
    private void end(boolean pressed) {
        if (ended) return;
        ended = true;
        main.removeCallbacks(look);
        main.removeCallbacks(choose);
        done.accept(pressed);
    }

    private void look() {
        if (entry == null) findEntry();
        else waitForPress();
    }

    private void findEntry() {
        AccessibilityNodeInfo found = onFirePage() ? find(steps[step]) : null;
        if (found != null) {
            choose = () -> select(found);
            main.postDelayed(choose, SETTLE_MS);
        } else if (SystemClock.elapsedRealtime() < limit) {
            main.postDelayed(look, POLL_MS);
        } else {
            end(false);
        }
    }

    /** The page takes a moment to settle, and until then a new selection does not hold. */
    private void select(AccessibilityNodeInfo found) {
        found.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        entry = found;
        windowId = service.getRootInActiveWindow() == null ? 0 : service.getRootInActiveWindow().getWindowId();
        focusBy = SystemClock.elapsedRealtime() + FOCUS_WAIT_MS;
        main.postDelayed(look, POLL_MS);
    }

    /** Told when the entry has to be found again: until `selected` hears of it, OK does nothing. */
    void whenLost(Runnable action) { lost = action; }

    /** Asked as OK goes down: the page may have moved since the last look. */
    boolean ready() { return announced && selectionHolds(); }

    /**
     * Whether the row that really has the selection is this step's. Fire OS keeps Launch application
     * first and selected, so an OK that finds Force stop no longer selected opens the app it was
     * meant to close: having selected a row says nothing about where the selection is now. A page
     * that does not say where its selection is gets the benefit of the doubt.
     */
    private boolean selectionHolds() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo focus = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return focus == null || says(focus, steps[step]);
    }

    private static boolean says(AccessibilityNodeInfo row, String[] names) {
        for (String name : names) {
            for (AccessibilityNodeInfo node : row.findAccessibilityNodeInfosByText(name)) {
                if (name.contentEquals(String.valueOf(node.getText()))) return true;
            }
        }
        return false;
    }

    /** TaskService hears OK. */
    void okPressed() {
        okPressed = entry != null;
    }

    /**
     * Pressed, an entry may also turn off or open another window. An entry gone is not a press: a
     * page seen for the first time fills in late and builds its rows again. Nor is a selection
     * that moved away: OK would land on another row, so until it is back no OK goes through.
     * `selected` hears of a row only once the selection is really on it.
     */
    private void waitForPress() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        boolean there = entry.refresh();
        boolean pressed = okPressed || root != null && root.getWindowId() != windowId || there && !entry.isEnabled();
        okPressed = false;
        long now = SystemClock.elapsedRealtime();
        if (pressed) {
            if (step + 1 < steps.length) {
                step++;
                entry = null;
                announced = false;
                limit = now + FIND_LIMIT_MS;
                main.postDelayed(look, POLL_MS);
            } else {
                end(true);
            }
        } else if (there && selectionHolds()) {
            if (!announced) {
                announced = true;
                limit = now + PRESS_LIMIT_MS;
                selected.accept(step);
            }
            if (now < limit) main.postDelayed(look, POLL_MS);
            else end(false);
        } else if (there && !announced && now < focusBy) {
            main.postDelayed(look, POLL_MS);
        } else if (announced) {
            entry = null;
            announced = false;
            if (lost != null) lost.run();
            limit = now + FIND_LIMIT_MS;
            main.postDelayed(look, POLL_MS);
        } else if (now < limit) {
            entry = null;
            main.postDelayed(look, POLL_MS);
        } else {
            end(false);
        }
    }

    /** Only here: Salotto's own pages use the same words. */
    private boolean onFirePage() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        return root != null && Fire.SETTINGS.contentEquals(String.valueOf(root.getPackageName()));
    }

    private AccessibilityNodeInfo find(String[] names) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return null;
        for (String name : names) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(name);
            for (AccessibilityNodeInfo node : nodes) {
                if (!name.contentEquals(String.valueOf(node.getText()))) continue;
                for (AccessibilityNodeInfo up = node; up != null; up = up.getParent()) {
                    if (up.isClickable() && up.isEnabled()) return up;
                }
            }
        }
        return null;
    }

    /** Back until Fire OS's pages are gone, then `after`. */
    void leave(Runnable after) {
        leave(after, MOST_BACKS);
    }

    private void leave(Runnable after, int backs) {
        if (backs == 0 || !onFirePage()) {
            after.run();
            return;
        }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        main.postDelayed(() -> leave(after, backs - 1), LEAVE_MS);
    }
}
