package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    /** The app in front (its windows' changes): Back over a trailer in YouTube comes back to its page (TrailerReturn). */
    private CharSequence front;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (TrailerReturn.onKey(this, event, front)) return true;
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || "com.android.systemui".contentEquals(pkg)) return;   // the volume bar isn't leaving the app
        // Left YouTube another way (Home, an app button): its Back is its own again.
        if (front != null && TrailerReturn.YOUTUBE.contentEquals(front) && !TrailerReturn.YOUTUBE.contentEquals(pkg)) {
            TrailerReturn.drop(this);
        }
        front = pkg;
    }

    @Override public void onInterrupt() {}
}
