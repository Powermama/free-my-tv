package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A trailer plays in the YouTube app (it starts fastest there), and Back from it comes back to the title's page: the
 * YouTube app would keep Back for its own home. The page leaves a note ("trailer-return": kind, id) as it opens
 * YouTube; the remote's key service (RemoteKeyService, its own process: so a file, not SharedPreferences) takes Back
 * while YouTube is in front and the note is there, and opens the page again. Leaving YouTube any other way drops the
 * note, and YouTube's Back is its own again.
 */
final class TrailerReturn {
    private TrailerReturn() {}

    static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String NOTE = "trailer-return";
    private static final long FOR_MS = 30 * 60_000L;   // a note older than a trailer could be is forgotten

    private static File note(Context c) {
        return new File(c.getFilesDir(), NOTE);
    }

    /** The page opens its trailer in YouTube: Back is to come back to it. */
    static void leave(Context c, String kind, int id) {
        try (FileOutputStream out = new FileOutputStream(note(c))) {
            out.write((kind + " " + id + " " + System.currentTimeMillis()).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            AppLog.w("trailer return: " + e.getMessage());
        }
    }

    static void drop(Context c) {
        //noinspection ResultOfMethodCallIgnored
        note(c).delete();
    }

    /** The key service: true when it took the key (Back over YouTube, with a note), and the page opens again. */
    static boolean onKey(Context c, KeyEvent e, CharSequence front) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_BACK || front == null || !YOUTUBE.contentEquals(front)) return false;
        File f = note(c);
        if (!f.exists()) return false;
        String[] parts;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[64];
            int n = in.read(b);
            parts = new String(b, 0, Math.max(0, n), StandardCharsets.US_ASCII).trim().split(" ");
        } catch (IOException ex) {
            return false;
        }
        if (parts.length != 3 || System.currentTimeMillis() - Long.parseLong(parts[2]) > FOR_MS) {
            drop(c);
            return false;
        }
        if (e.getAction() != KeyEvent.ACTION_UP) return true;   // the press too: YouTube never sees this Back
        drop(c);
        c.startActivity(new Intent(c, TitleActivity.class).putExtra(TitleActivity.EXTRA_KIND, parts[0])
                .putExtra(TitleActivity.EXTRA_ID, Integer.parseInt(parts[1])).putExtra(TitleActivity.EXTRA_AGAIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        return true;
    }
}
