package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The open watch log (PRD R15), first part: what each profile started from this home screen (a pick's banner or page),
 * kept on the TV. Netflix and Prime Video tell Android nothing about what plays (no title in their media session, no
 * Watch Next rows), but a title opened from here is known: it joins Continue Watching beside the TV's own list, newest
 * first, and opens in its app again. The newest 20 per profile; only :home writes it.
 */
final class WatchLog {
    private WatchLog() {}

    private static final int KEEP = 20;
    private static final String IMAGES = "https://image.tmdb.org/t/p/w780";   // the card's picture, as TMDB sizes it

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("watchlog", Context.MODE_PRIVATE);
    }

    /** Whose log: the profile watching now, or the TV's own without profiles. */
    private static String who(Context c) {
        Profiles.Profile p = Profiles.active(c);
        return p == null ? "tv" : p.id;
    }

    /** A pick was opened in its app from here. */
    static void started(Context c, Recs.Pick p) {
        if (p == null || p.pkg == null || p.id < 0) return;
        String key = who(c);
        JSONArray old = list(c, key), out = new JSONArray();
        try {
            out.put(new JSONObject().put("kind", p.kind).put("id", p.id).put("title", p.title).put("pkg", p.pkg)
                    .put("app", Recs.serviceName(p)).put("link", p.link == null ? "" : p.link)
                    .put("backdrop", p.backdrop == null ? "" : p.backdrop).put("at", System.currentTimeMillis()));
            for (int i = 0; i < old.length() && out.length() < KEEP; i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.optInt("id") == p.id && p.kind.equals(o.optString("kind"))) continue;   // once, the newest
                out.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        sp(c).edit().putString(key, out.toString()).apply();
    }

    private static JSONArray list(Context c, String key) {
        try {
            return new JSONArray(sp(c).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** The profile's log as Continue Watching cards: no progress (the apps don't tell), the TMDB picture. */
    static List<WatchNext.Item> items(Context c) {
        List<WatchNext.Item> out = new ArrayList<>();
        JSONArray a = list(c, who(c));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || HomeApps.launchIntent(c.getPackageManager(), o.optString("pkg")) == null) continue;
            String backdrop = o.optString("backdrop");
            WatchNext.Item it = new WatchNext.Item(o.optString("pkg"), null, backdrop.isEmpty() ? null : IMAGES + backdrop);
            it.title = o.optString("title");
            it.app = o.optString("app");
            it.engaged = o.optLong("at");
            String link = o.optString("link");
            it.intent = link.isEmpty() ? null : new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(it.pkg).toUri(Intent.URI_INTENT_SCHEME);
            out.add(it);
        }
        return out;
    }
}
