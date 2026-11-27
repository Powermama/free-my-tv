package io.freemytv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A title's page, before its app, as the Apple TV app has it: the backdrop over the whole screen; the title; one quiet
 * line of what it is (year, genres, runtime or seasons, age rating, score); a few lines about it; then where it streams
 * (the app on the TV first, white, the others beside it, "Get" where the app is missing) and its trailer.
 * While the details come, grey bars stand where the words will be and breathe (their alpha only: nothing is drawn
 * again for it); the picture fades in when it is there. Plain Android views; Back goes home.
 */
public class TitleActivity extends Activity implements Recs.Listener {
    static final String EXTRA_KIND = "io.freemytv.KIND", EXTRA_ID = "io.freemytv.ID";
    /** Opened again by Back from its trailer in YouTube (TrailerReturn): in a task of its own, so Back goes home. */
    static final String EXTRA_AGAIN = "io.freemytv.AGAIN";
    private static final int SKELETON = 0x33F5F5F7;

    private float k;
    private Recs recs;
    private Recs.Pick pick;
    private ImageView backdrop;
    private TextView meta, overview;
    private LinearLayout badges;
    private LinearLayout skeleton, buttons;
    private ObjectAnimator breathing;
    private String trailerKey;
    private boolean pictureShown;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        k = getResources().getDisplayMetrics().widthPixels / 1920f;
        recs = Recs.get(this);
        pick = recs.find(getIntent().getStringExtra(EXTRA_KIND), getIntent().getIntExtra(EXTRA_ID, 0));
        if (pick == null) {   // gone from this hour's list meanwhile
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0E0E10);
        root.setClipChildren(false);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Darker at the left, where the text is, and at the foot: the picture stays, the words read.
        View side = new View(this);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xE60E0E10, 0x990E0E10, 0x000E0E10}));
        root.addView(side, new FrameLayout.LayoutParams(Math.round(1300 * k), FrameLayout.LayoutParams.MATCH_PARENT));
        View foot = new View(this);
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x000E0E10, 0xF20E0E10}));
        root.addView(foot, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Math.round(520 * k), Gravity.BOTTOM));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.BOTTOM);
        column.setClipChildren(false);
        int pad = Math.round(80 * k);
        column.setPadding(pad, pad, pad, Math.round(96 * k));
        column.setClipToPadding(false);   // a focused button grows past the padding
        root.addView(column, new FrameLayout.LayoutParams(Math.round(1100 * k), FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = WhoActivity.text(this, k, 72, Ui.displaySemibold, WhoActivity.INK);
        title.setText(pick.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(title, WhoActivity.wrap(0, 0, 0, Math.round(22 * k)));
        // The scores and the age rating, as small outlined badges: read at a glance, apart from the line of facts.
        badges = new LinearLayout(this);
        badges.setVisibility(View.GONE);
        column.addView(badges, WhoActivity.wrap(0, 0, 0, Math.round(20 * k)));
        meta = WhoActivity.text(this, k, 28, Ui.medium, 0xB3F5F5F7);
        meta.setVisibility(View.GONE);
        column.addView(meta, WhoActivity.wrap(0, 0, 0, Math.round(26 * k)));
        overview = WhoActivity.text(this, k, 28, Ui.regular, 0xD9F5F5F7);
        overview.setMaxLines(4);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(8 * k, 1);
        overview.setVisibility(View.GONE);
        column.addView(overview, WhoActivity.wrap(0, 0, 0, Math.round(44 * k)));
        // Until the details come: one bar for the line, three for the text, the last one shorter.
        skeleton = new LinearLayout(this);
        skeleton.setOrientation(LinearLayout.VERTICAL);
        skeleton.addView(bar(420, 26), WhoActivity.wrap(0, 0, 0, Math.round(34 * k)));
        for (int w : new int[] {900, 860, 560}) skeleton.addView(bar(w, 24), WhoActivity.wrap(0, 0, 0, Math.round(18 * k)));
        column.addView(skeleton, WhoActivity.wrap(0, 0, 0, Math.round(44 * k)));
        breathing = ObjectAnimator.ofFloat(skeleton, View.ALPHA, 1f, 0.45f);
        breathing.setDuration(900);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.start();

        // Where it streams: the pick's own app now (it's known), the others when the details say so.
        buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        column.addView(buttons, WhoActivity.wrap(0, 0, 0, 0));
        List<String[]> first = new ArrayList<>();
        if (pick.pkg != null) first.add(new String[] {Recs.serviceName(pick), pick.pkg});
        showButtons(first);
        setContentView(root);

        showBackdrop();
        recs.details(pick, this::showDetails);
    }

    private View bar(int width, int height) {
        View v = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setColor(SKELETON);
        d.setCornerRadius(height * k / 2);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.round(width * k), Math.round(height * k)));
        return v;
    }

    /** One button per service ({name, package}): the first white ("Open in Netflix"), the others quieter; then Trailer. */
    private void showButtons(List<String[]> services) {
        View focused = getCurrentFocus();
        int focusedAt = focused == null ? 0 : Math.max(0, buttons.indexOfChild(focused));
        buttons.removeAllViews();
        for (int i = 0; i < services.size() && i < 3; i++) {
            String[] s = services.get(i);
            String label = HomeApps.watchLabel(this, s[1], s[0]);
            if (label == null) continue;
            if (i > 0 && HomeApps.launchIntent(getPackageManager(), s[1]) != null) label = s[0];   // "Prime Video"
            TextView b = button(label, i == 0 ? 30 : 26, i == 0 ? Ui.semibold : Ui.medium);
            b.setOnClickListener(v -> {
                if (HomeApps.watch(this, s[1], s[0], s[1].equals(pick.pkg) ? pick.link : null)) finish();
            });
        }
        if (trailerKey != null) {
            TextView t = button("▶  " + Ui.tr("Trailer"), 26, Ui.medium);
            t.setOnClickListener(v -> openTrailer());
        }
        View again = buttons.getChildAt(Math.min(focusedAt, buttons.getChildCount() - 1));
        if (again != null) again.requestFocus();
    }

    private TextView button(String label, float size, android.graphics.Typeface face) {
        TextView b = WhoActivity.text(this, k, size, face, WhoActivity.INK);
        b.setText(label);
        WhoActivity.pill(b, k);
        buttons.addView(b, WhoActivity.wrap(buttons.getChildCount() == 0 ? 0 : Math.round(20 * k), 0, 0, 0));
        return b;
    }

    @Override protected void onResume() {
        super.onResume();
        if (recs != null) recs.addListener(this);
    }

    @Override protected void onPause() {
        if (recs != null) recs.removeListener(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (breathing != null) breathing.cancel();
        super.onDestroy();
    }

    /** A picture came: maybe ours. */
    @Override public void onRecs() {
        showBackdrop();
    }

    private void showBackdrop() {
        if (pictureShown) return;
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        Recs.Poster picture = recs.banner(pick, w, h);
        if (picture == null) return;
        pictureShown = true;
        backdrop.setImageBitmap(picture.bitmap);
        backdrop.animate().alpha(1f).setDuration(350).start();
    }

    /** "2026 · Animation, Comedy · 1 h 54 min · FSK 6 · ★ 7.1", the overview, the services, the trailer. */
    private void showDetails(JSONObject d) {
        if (isFinishing()) return;
        breathing.cancel();
        skeleton.setVisibility(View.GONE);
        if (d == null) {   // offline or TMDB away: what the list knew
            meta.setText(pick.meta == null ? "" : pick.meta);
            meta.setVisibility(View.VISIBLE);
            return;
        }
        List<String> parts = new ArrayList<>();
        boolean movie = "movie".equals(pick.kind);
        String date = d.optString(movie ? "release_date" : "first_air_date", "");
        if (date.length() >= 4) parts.add(date.substring(0, 4));
        JSONArray genres = d.optJSONArray("genres");
        if (genres != null && genres.length() > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(2, genres.length()); i++) names.add(genres.optJSONObject(i).optString("name"));
            parts.add(TextUtils.join(", ", names));
        }
        if (movie) {
            int min = d.optInt("runtime", 0);
            if (min > 0) parts.add(min >= 60 ? Ui.trf("{h} h {m} min", "h", min / 60, "m", min % 60) : Ui.trf("{n} min", "n", min));
        } else {
            int seasons = d.optInt("number_of_seasons", 0);
            if (seasons > 0) parts.add(Ui.plural("plural|one:{n} season|other:{n} seasons", seasons));
        }
        badges.removeAllViews();
        double score = d.optDouble("vote_average", 0);
        if (score > 0 && d.optInt("vote_count", 0) >= 20) badge("★ " + String.format(Ui.locale(), "%.1f", score), "TMDB");
        String age = ageRating(d, movie);
        if (!age.isEmpty()) badge(Ui.trf("Rated {age}", "age", age), null);
        badges.setVisibility(badges.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        meta.setText(TextUtils.join("  ·  ", parts));
        meta.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
        String about = d.optString("overview", "");
        overview.setText(about);
        overview.setVisibility(about.isEmpty() ? View.GONE : View.VISIBLE);
        for (View v : new View[] {badges, meta, overview}) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(250).start();
        }
        trailerKey = trailer(d);
        List<String[]> services = recs.streams(d);
        // The pick's own app first: it's the one the rows promised.
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i)[1].equals(pick.pkg)) {
                services.add(0, services.remove(i));
                break;
            }
        }
        if (services.isEmpty() && pick.pkg != null) services.add(new String[] {Recs.serviceName(pick), pick.pkg});
        showButtons(services);
    }

    /** A badge: its value bright, its source quieter beside it ("★ 7.1  TMDB"), in a thin rounded outline. */
    private void badge(String value, String source) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(10 * k);
        frame.setStroke(Math.max(1, Math.round(2 * k)), 0x66F5F5F7);
        b.setBackground(frame);
        int h = Math.round(16 * k), v = Math.round(7 * k);
        b.setPadding(h, v, h, v);
        TextView t = WhoActivity.text(this, k, 26, Ui.semibold, WhoActivity.INK);
        t.setText(value);
        b.addView(t);
        if (source != null) {
            TextView s = WhoActivity.text(this, k, 22, Ui.medium, 0x99F5F5F7);
            s.setText(source);
            b.addView(s, WhoActivity.wrap(Math.round(10 * k), 0, 0, 0));
        }
        badges.addView(b, WhoActivity.wrap(badges.getChildCount() == 0 ? 0 : Math.round(14 * k), 0, 0, 0));
    }

    /** The age rating for this country (FSK in Germany), if TMDB has one. */
    private static String ageRating(JSONObject d, boolean movie) {
        String region = Place.region();
        JSONObject block = d.optJSONObject(movie ? "release_dates" : "content_ratings");
        JSONArray results = block == null ? null : block.optJSONArray("results");
        if (results == null) return "";
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null || !region.equalsIgnoreCase(r.optString("iso_3166_1"))) continue;
            if (!movie) return r.optString("rating", "");
            JSONArray dates = r.optJSONArray("release_dates");
            for (int j = 0; dates != null && j < dates.length(); j++) {
                String c = dates.optJSONObject(j).optString("certification", "");
                if (!c.isEmpty()) return c;
            }
        }
        return "";
    }

    /** The first YouTube trailer (the page's language first, then English), or null. */
    private static String trailer(JSONObject d) {
        JSONObject videos = d.optJSONObject("videos");
        JSONArray list = videos == null ? null : videos.optJSONArray("results");
        for (int pass = 0; list != null && pass < 2; pass++) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject v = list.optJSONObject(i);
                if (v == null || !"YouTube".equals(v.optString("site"))) continue;
                if (pass == 0 && !"Trailer".equals(v.optString("type"))) continue;
                String key = v.optString("key", "");
                if (!key.isEmpty()) return key;
            }
        }
        return null;
    }

    /**
     * In the YouTube app, where it starts fastest ("https://www.youtube.com/watch?v=": "vnd.youtube:" only opened its
     * home on this TV); Back there comes back here (TrailerReturn).
     */
    private void openTrailer() {
        if (trailerKey == null) return;
        TrailerReturn.leave(this, pick.kind, pick.id, getTaskId());
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=" + trailerKey))
                    .setPackage(TrailerReturn.YOUTUBE));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            TrailerReturn.drop(this);
            AppLog.w("trailer: " + e.getMessage());
        }
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        // Opened again from its trailer, it's alone in its task: under it is YouTube. Home instead.
        if (getIntent().getBooleanExtra(EXTRA_AGAIN, false)) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
