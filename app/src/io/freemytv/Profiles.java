package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The family's profiles for "Who's watching?": a name and an avatar each, who is watching now, and whether to ask
 * again. Without profiles nothing changes. The picker comes at a real power-on: a new boot, or standby, seen as the
 * screen going off (while :home runs) or as time the system spent suspended (uptime stops in deep sleep, elapsed
 * realtime doesn't), which also counts while :home was killed. The screensaver does neither. Only :home uses them.
 */
final class Profiles {
    private Profiles() {}

    static final int MAX = 5;
    private static final String AVATARS = "avatars";

    static final class Profile {
        final String id, name, avatar;

        Profile(String id, String name, String avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("profiles", Context.MODE_PRIVATE);
    }

    static List<Profile> all(Context c) {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                out.add(new Profile(o.getString("id"), o.getString("name"), o.getString("avatar")));
            }
        } catch (JSONException e) {
            AppLog.w("profiles: unreadable, starting empty");
        }
        return out;
    }

    private static void write(Context c, List<Profile> list) {
        JSONArray out = new JSONArray();
        try {
            for (Profile p : list) out.put(new JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar));
        } catch (JSONException e) {
            throw new IllegalStateException(e);   // only strings go in
        }
        sp(c).edit().putString("list", out.toString()).apply();
    }

    /** The one watching now: the last picked, or the first while none was. Null without profiles. */
    static Profile active(Context c) {
        List<Profile> list = all(c);
        String id = sp(c).getString("active", "");
        for (Profile p : list) if (p.id.equals(id)) return p;
        return list.isEmpty() ? null : list.get(0);
    }

    /** Adds a new profile (id null) or replaces the one with that id. Returns its id. */
    static String save(Context c, String id, String name, String avatar) {
        List<Profile> list = all(c);
        Profile p = new Profile(id == null ? UUID.randomUUID().toString() : id, name, avatar);
        int at = indexOf(list, p.id);
        if (at >= 0) list.set(at, p);
        else list.add(p);
        write(c, list);
        return p.id;
    }

    static void remove(Context c, String id) {
        List<Profile> list = all(c);
        int at = indexOf(list, id);
        if (at < 0) return;
        list.remove(at);
        write(c, list);
    }

    private static int indexOf(List<Profile> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return -1;
    }

    /** Someone was picked: they watch until the next power-on. */
    static void pick(Context c, String id) {
        sp(c).edit().putString("active", id).putInt("boot", bootCount(c)).putLong("slept", slept())
                .putBoolean("ask", false).apply();
    }

    /** The screen went off (standby): ask at the next home screen. */
    static void screenOff(Context c) {
        if (!all(c).isEmpty()) sp(c).edit().putBoolean("ask", true).apply();
    }

    static boolean shouldAsk(Context c) {
        if (all(c).isEmpty()) return false;
        SharedPreferences p = sp(c);
        return p.getBoolean("ask", false) || p.getInt("boot", -2) != bootCount(c)
                || slept() - p.getLong("slept", 0) > SLEPT_MS;
    }

    /** More suspended than this since the pick: the TV was in standby (a screen-on TV never suspends). */
    private static final long SLEPT_MS = 10_000;

    /** How long the system has been suspended since it started. */
    private static long slept() {
        return SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
    }

    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** The bundled avatars (tools/make_avatars.py), by name, in a fixed order. */
    static List<String> avatars(Context c) {
        try {
            String[] names = c.getAssets().list(AVATARS);
            List<String> out = new ArrayList<>();
            if (names == null) return out;
            Arrays.sort(names);
            for (String n : names) if (n.endsWith(".png")) out.add(n);   // not LICENSE.txt
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** An avatar's picture at about `size` px, or null. */
    static Bitmap avatar(Context c, String name, int size) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = size > 0 && size <= 128 ? 2 : 1;   // the files are 256 px
        try (InputStream in = c.getAssets().open(AVATARS + "/" + name)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            return null;
        }
    }
}
