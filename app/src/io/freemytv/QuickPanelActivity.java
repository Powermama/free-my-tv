package io.freemytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick settings panel a TCL remote's Settings button can open in Free My TV mode (a Remote Buttons choice; TV
 * Settings is the default), over whatever is playing (Google TV's
 * own panel lives in its home app, which is off in our mode): the time, and tiles for the inputs, the TV's picture and
 * sound panels, network, remotes and accessories, accessibility and all settings. Each tile opens the TV's own screen;
 * Inputs lists them right here. Back, or the Settings button again, closes it.
 * The home screen's Left key opens it as the sidebar (EXTRA_SIDEBAR, the PRD's Screen 2): who is watching, Home, Apps,
 * Inputs (Live TV's among them), the TV's own Settings, Recent Apps and Get apps last, quiet; Inputs goes one level
 * in, and Right or Back closes it. The home screen stays where it is, dimmed behind it.
 * Drawn as liquid glass: TVs turn window blur off (and video plays on its own plane, under any blur), so the glass is
 * painted, nearly opaque so it reads over anything: the home screen's look (white with dark ink by day, smoked with
 * white ink at night, Settings > Theme), faint light inside, bright rims, and one focus lens that flows between the tiles.
 */
public class QuickPanelActivity extends Activity {
    /** A tile: its label, icon, accent color and the screens it may open, the first one the TV has wins. */
    private static final class Tile {
        final String label, symbol;
        final int color;
        final Intent[] intents;

        String hint;   // the settings page: one quiet line of what's inside

        Tile(String label, String symbol, int color, Intent... intents) {
            this.label = label;
            this.symbol = symbol;
            this.color = color;
            this.intents = intents;
        }

        Tile hint(String text) {
            hint = text;
            return this;
        }
    }

    /**
     * The home screen's HDMI button: only the Inputs list, as a small popover under the top-right buttons, so nothing
     * on the home screen moves. Back closes it.
     */
    static final String EXTRA_INPUTS = "io.freemytv.extra.INPUTS_ONLY";
    static final String EXTRA_SIDEBAR = "io.freemytv.extra.SIDEBAR";
    /** The settings page (the sidebar's Settings): the same glass and tiles over the whole screen, four across. */
    static final String EXTRA_HUB = "io.freemytv.extra.HUB";
    /** The settings page's second level: Free My TV's own settings as cards (for whoever looks after the TV). */
    static final String EXTRA_ADMIN = "io.freemytv.extra.ADMIN";
    /** The sidebar's own rows, by key; the others are the home screen's top buttons and open as they do. */
    private static final String SIDE_PROFILE = "side:profile", SIDE_HOME = "side:home", SIDE_APPS = "side:apps",
            SIDE_STORE = "side:store";
    /** The sidebar in a 1920×1080 space: its width, and how much the home screen behind it dims. */
    private static final float SIDE_W = 520, SIDE_DIM = 0.35f;
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final int INPUTS = 0;
    /** The panel in a 1920×1080 space: its size and the margin to the screen's corner. */
    private static final float W = 600, H = 800, MARGIN = 40;
    /** The popover: its width, and where it sits, just under the home screen's top-right buttons. */
    private static final float POP_W = 520, POP_RIGHT = 76, POP_TOP = 196;

    private PanelView view;
    private Click click;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        click = new Click(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float k = dm.widthPixels / 1920f;
        boolean only = getIntent().getBooleanExtra(EXTRA_INPUTS, false);
        boolean side = !only && getIntent().getBooleanExtra(EXTRA_SIDEBAR, false);
        boolean hub = !only && !side && getIntent().getBooleanExtra(EXTRA_HUB, false);
        List<HomeApps.Item> inputs = only ? HomeApps.orderedInputs(this) : null;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (hub) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = lp.y = 0;
            lp.width = dm.widthPixels;
            lp.height = dm.heightPixels;
        } else if (side) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.round(MARGIN * k);
            lp.y = Math.round(MARGIN * k);
            lp.width = Math.round(SIDE_W * k);
            lp.height = dm.heightPixels - 2 * Math.round(MARGIN * k);
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            lp.dimAmount = SIDE_DIM;
        } else if (only) {
            int rows = Math.max(1, Math.min(inputs.size(), PanelView.ROWS_VISIBLE));
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Math.round(POP_RIGHT * k);
            lp.y = Math.round(POP_TOP * k);
            lp.width = Math.round(POP_W * k);
            lp.height = Math.round((2 * PanelView.POP_INSET + rows * (PanelView.ROW_H + PanelView.ROW_GAP)
                    - PanelView.ROW_GAP) * k);
        } else {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.round(MARGIN * k);
            lp.y = Math.round(MARGIN * k);
            lp.width = Math.round(W * k);
            lp.height = Math.round(H * k);
        }
        getWindow().setAttributes(lp);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        overridePendingTransition(0, 0);   // the panel springs in by itself
        boolean admin = hub && getIntent().getBooleanExtra(EXTRA_ADMIN, false);
        view = new PanelView(this, k, admin ? adminTiles() : hub ? hubTiles() : tiles(), inputs, side ? sidebar() : null, hub);
        setContentView(view);
    }

    private Tile[] tiles() {
        return new Tile[] {
            new Tile(Ui.tr("Inputs"), "hdmi", 0xFF30B0C7),
            new Tile(Ui.tr("Display"), "tv_settings", 0xFFFF9F0A,
                    new Intent(Settings.ACTION_DISPLAY_SETTINGS).setPackage(TV_SETTINGS),
                    new Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            new Tile(Ui.tr("Sound"), "speaker", 0xFFFF375F,
                    new Intent("com.android.tv.action.VIEW_SOUND_SETTINGS").setPackage(TV_SETTINGS),
                    new Intent(Settings.ACTION_SOUND_SETTINGS)),
            new Tile(Ui.tr("Network"), "wifi", 0xFF0A84FF, new Intent(Settings.ACTION_WIFI_SETTINGS)),
            new Tile(Ui.tr("Bluetooth"), "bluetooth", 0xFF3F7CFF,
                    new Intent("com.android.tv.settings.DEVICES").setPackage(TV_SETTINGS),
                    new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            new Tile(Ui.tr("Accessibility"), "accessibility", 0xFF30D158, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            new Tile(Ui.tr("Settings"), "settings", 0xFF8E8E9A, new Intent(Settings.ACTION_SETTINGS)),
        };
    }

    /**
     * The settings page's tiles: what the TV's own settings app holds, each straight to its screen, then the
     * screensaver, all of the TV's settings, and Free My TV's own (for whoever looks after the TV).
     */
    private Tile[] hubTiles() {
        Tile[] base = tiles();
        Tile[] out = new Tile[base.length + 2];
        int n = 0;
        for (Tile t : base) {
            if (t.symbol.equals("settings")) continue;   // "All settings" goes last, below
            // "Picture", as Apple TV calls it, where the quick panel says "Display".
            out[n++] = t.symbol.equals("tv_settings") ? new Tile(Ui.tr("Picture"), t.symbol, t.color, t.intents) : t;
        }
        out[n++] = new Tile(Ui.tr("Screensaver"), "bedtime", 0xFF5E5CE6, new Intent(Settings.ACTION_DREAM_SETTINGS));
        out[n++] = new Tile(Ui.tr("All settings"), "settings", 0xFF8E8E9A, new Intent(Settings.ACTION_SETTINGS));
        out[n++] = new Tile("Free My TV", ADMIN_SYMBOL, 0xFFFF375F);   // one level in (adminTiles), not an intent
        for (int i = 0; i < n; i++) out[i].hint(hubHint(out[i].symbol));
        return java.util.Arrays.copyOf(out, n);
    }

    private static final String ADMIN_SYMBOL = "sparkles";

    /** The settings page's tiles, by symbol: what each holds, in a few words. */
    private static String hubHint(String symbol) {
        switch (symbol) {
            case "hdmi": return Ui.tr("HDMI and Live TV");
            case "tv_settings": return Ui.tr("Picture mode, brightness");
            case "speaker": return Ui.tr("Sound mode, output");
            case "wifi": return Ui.tr("Wi-Fi and Ethernet");
            case "bluetooth": return Ui.tr("Remotes, headphones");
            case "accessibility": return Ui.tr("Captions, text, contrast");
            case "bedtime": return Ui.tr("When it starts");
            case "settings": return Ui.tr("The TV's full menu");
            case ADMIN_SYMBOL: return Ui.tr("Home screen, profiles");
            default: return null;
        }
    }

    /** Free My TV's own settings, grouped as cards; each opens the settings straight at its screen (EXTRA_ROW). */
    private Tile[] adminTiles() {
        return new Tile[] {
            new Tile(Ui.tr("Home Screen"), "home", 0xFF0A84FF, row(SettingsActivity.ROW_HOME)).hint(Ui.tr("Free My TV or the original")),
            new Tile(Ui.tr("Home Settings"), "tv", 0xFF5E5CE6, row(SettingsActivity.ROW_HOME_SETTINGS)).hint(Ui.tr("Language, look, picks")),
            new Tile(Ui.tr("Profiles"), "person", 0xFFFF9F0A,
                    new Intent(this, WhoActivity.class).putExtra(WhoActivity.EXTRA_MANAGE, true)).hint(Ui.tr("Names and avatars")),
            new Tile(Ui.tr("Shelf"), "apps", 0xFF30D158, row(SettingsActivity.ROW_SHELF)).hint(Ui.tr("Apps and their order")),
            new Tile(Ui.tr("Inputs"), "hdmi", 0xFF30B0C7, row(SettingsActivity.ROW_INPUTS)).hint(Ui.tr("Names and order")),
            new Tile(Ui.tr("Remote Buttons"), "smart_button", 0xFF8E8E9A, row(SettingsActivity.ROW_REMOTE)).hint(Ui.tr("What the app buttons open")),
            new Tile(Ui.tr("Sleep Timer"), "bedtime", 0xFFBF5AF2, row(SettingsActivity.ROW_SLEEP)).hint(Ui.tr("Bedtime and turning off")),
            new Tile(Ui.tr("Screensaver"), "play", 0xFFFF375F, row(SettingsActivity.ROW_SAVER)).hint(Ui.tr("See it now")),
        };
    }

    private Intent row(int row) {
        return new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_ROW, row);
    }

    /**
     * The sidebar's rows: who is watching (OK: "Who's watching?"), Home, Apps, then the home screen's top buttons as
     * they are set (Live TV or the input it opens, HDMI Inputs, Settings), and the store last.
     */
    private List<HomeApps.Item> sidebar() {
        List<HomeApps.Item> rows = new ArrayList<>();
        Profiles.Profile p = Profiles.active(this);
        rows.add(HomeApps.action(HomeApps.SETTINGS, SIDE_PROFILE, p == null ? Ui.tr("Who's watching?") : p.name, "person"));
        rows.add(HomeApps.action(HomeApps.SETTINGS, SIDE_HOME, Ui.tr("Home"), "home"));
        rows.add(HomeApps.action(HomeApps.ALL_APPS, SIDE_APPS, Ui.tr("Apps"), "apps"));
        for (HomeApps.Item it : HomeApps.topButtons(this)) {
            // Live TV (or the input it opens) is one level in, under Inputs: one row fewer.
            if (it.kind == HomeApps.RECENTS || HomeApps.TOP_STORE.equals(it.key) || HomeApps.TOP_LIVE.equals(it.key)) continue;
            // Settings: the TV's own (picture, sound, network); Free My TV's stay behind its icon in Apps.
            rows.add(it.kind == HomeApps.SETTINGS ? HomeApps.tvSettings() : it);
        }
        // Recent Apps, to close what still runs: where the drawer held it (Xiaomi's remotes have no key for it).
        if (!Prefs.recents().equals("off")) rows.add(HomeApps.action(HomeApps.RECENTS, HomeApps.TOP_RECENTS, Ui.tr("Recent Apps"), "recents"));
        String store = HomeApps.storePackage(this);
        if (store != null) {
            HomeApps.Item it = HomeApps.action(HomeApps.APP, SIDE_STORE, Ui.tr("Get apps"), "store");
            it.pkg = store;
            rows.add(it);
        }
        return rows;
    }

    /** A sidebar row's chip color, as the quick settings tiles have theirs. */
    private static int sideColor(HomeApps.Item it) {
        if (SIDE_HOME.equals(it.key)) return 0xFF0A84FF;
        if (SIDE_APPS.equals(it.key)) return 0xFF5E5CE6;
        if (SIDE_STORE.equals(it.key)) return 0xFF30D158;
        switch (it.kind) {
            case HomeApps.LIVE_TV:
            case HomeApps.INPUT: return 0xFFFF9F0A;
            case HomeApps.INPUTS: return 0xFF30B0C7;
            case HomeApps.RECENTS: return 0xFFBF5AF2;
            case HomeApps.ANDROID_SETTINGS: return 0xFF8E8E9A;
            default: return 0xFF8E8E9A;
        }
    }

    /** The Settings button again (RemoteKeys starts this activity a second time): closes the panel. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        view.close();
    }

    /**
     * Leaving for another app or the home screen closes it, like Google TV's panel; the settings page stays, so Back
     * from the screen a tile opened comes back to it.
     */
    @Override protected void onStop() {
        super.onStop();
        if (!isFinishing() && !getIntent().getBooleanExtra(EXTRA_HUB, false)) finish();
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onDestroy() {
        click.release();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean open(Intent... intents) {
        for (Intent intent : intents) {
            try {
                startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                AppLog.w("quick panel: couldn't open " + intent + ": " + e.getMessage());
            }
        }
        return false;
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    private final class PanelView extends View {
        private static final float PAD = 40, GAP = 14, TILE_H = 132, GRID_TOP = 184, ROW_H = 76, ROW_GAP = 10,
                CARD_R = 46, TILE_R = 30, CHIP_R = 25, SYMBOL = 24;
        /** The sidebar: the profile row (taller, with the avatar), then the rows, and a line over the last one. */
        private static final float SIDE_PAD = 22, PROFILE_H = 104, SIDE_AVATAR = 64, SIDE_RULE = 28;
        /** The settings page: its margins and the room between its tiles. */
        private static final float HUB_PAD = 120, HUB_GAP = 28;
        /** The popover: no clock and no title (the HDMI button's label above it says what it is), a slim inset. */
        private static final float POP_INSET = 22, POP_CARD_R = 46;   // concentric: the rows' 24 + the inset; the lens glow (21) fits inside
        private static final int ROWS_VISIBLE = 6;
        /** TalkBack's ids: a tile is its index + 1, an Inputs row ROW_IDS + its index + 1. */
        private static final int ROW_IDS = 1000;

        private final float k;
        private final Tile[] tiles;
        private final boolean inputsOnly;   // the HDMI button's popover: no clock, no tiles, no title
        private final boolean hub;          // the settings page: the whole screen, a title, four tiles across
        private final int cols;
        private final float tileH, gridTop;
        private String status = "";
        private final List<HomeApps.Item> side;   // non-null: the sidebar, these rows in place of the clock and tiles
        private final int[] sideColors;
        private int sideFocus;
        private Bitmap avatar;   // who is watching, in the sidebar's first row
        private BitmapShader avatarShader;
        private final float inset, cardR, rowsTop;
        private List<HomeApps.Item> inputs;   // non-null: the Inputs list is showing (or fading out)
        private boolean inputsOpen, closing;
        private int focus, inputFocus;

        // Motion: the panel coming in, the focus lens (position and size), a press, the grid / Inputs list swap.
        private final Spring show = new Spring(0.5f, 0.78f, 0);
        private final Spring lensX = new Spring(0.36f, 0.72f, 0), lensY = new Spring(0.36f, 0.72f, 0);
        private final Spring lensW = new Spring(0.36f, 0.8f, 0), lensH = new Spring(0.36f, 0.8f, 0);
        private final Spring press = new Spring(0.22f, 0.6f, 0);
        private final Spring page = new Spring(0.4f, 0.9f, 0);
        private long lastFrame;

        // The home screen's look: white glass with dark ink by day, smoked glass with white ink at night.
        private final boolean night;
        private final int ink, focusInk, tileFill;
        private final String inputsTitle = Ui.tr("Inputs"), noInputs = Ui.tr("No inputs");
        private String dateText = "", clockText = "";
        private float clockBearing;
        private final Spoken spoken;

        private final Paint date, clock, label, rowLabel, title, empty, hintPaint;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint layer = new Paint(), atop = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF(), card = new RectF();
        private LinearGradient tint, sheen, cardRim, tileRim, lens;
        private RadialGradient[] glows;
        private final float[] glowX = {0.2f, 0.95f, 0.3f}, glowY = {0.15f, 0.55f, 0.95f};
        // Each chip color's gradient, made once and looked up by color (no boxing in onDraw).
        private final int[] chipColor = new int[16];
        private final LinearGradient[] chipShader = new LinearGradient[16];
        private int chips;
        private final float[] tileX = new float[12], tileY = new float[12], labelSize = new float[12];   // labelSize: shrunk to fit long words
        private float tileW;

        private final Runnable frame = new Runnable() {
            @Override public void run() {
                invalidate();
            }
        };
        /** Once a minute: the date and time, formatted here rather than every frame. */
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                dateText = Ui.date(now);
                clockText = Ui.clock(now);
                clockBearing = Ui.leftBearing(clock, clockText);
                invalidate();
                postDelayed(this, 60_000 - now % 60_000);
            }
        };

        PanelView(Context c, float k, Tile[] tiles, List<HomeApps.Item> only, List<HomeApps.Item> side, boolean hub) {
            super(c);
            this.k = k;
            this.tiles = tiles;
            this.side = side;
            this.hub = hub;
            cols = hub ? 4 : 2;
            tileH = (hub ? 200 : TILE_H) * k;
            gridTop = hub ? 300 : GRID_TOP;
            if (hub) {
                status = status(c);
                show.snap(1);   // the whole screen: there at once (a slide or a fade would redraw it all on every frame)
            }
            inputsOnly = only != null;
            sideColors = new int[side == null ? 0 : side.size()];
            for (int i = 0; i < sideColors.length; i++) sideColors[i] = sideColor(side.get(i));
            Profiles.Profile who = side == null ? null : Profiles.active(c);
            if (who != null) {
                avatar = Profiles.avatar(c, who.avatar, Math.round(SIDE_AVATAR * k));
                if (avatar != null) avatarShader = new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            inset = inputsOnly ? POP_INSET : PAD;
            cardR = inputsOnly ? POP_CARD_R : CARD_R;
            rowsTop = inputsOnly ? POP_INSET : side != null ? SIDE_PAD + 74 : (hub ? 300 : GRID_TOP) + 58;
            if (inputsOnly) {
                inputs = only;
                inputsOpen = true;
                inputFocus = startRow(only);
                page.snap(1);
            }
            night = Config.SIDEBAR || HomeData.get(c).isNight(System.currentTimeMillis());   // the page is always dark
            ink = night ? 0xFFFFFFFF : 0xFF1D1D1F;
            focusInk = night ? 0xFF1D1D1F : 0xFFFFFFFF;
            tileFill = night ? 0x17FFFFFF : 0x0D000000;
            date = Ui.text(Ui.medium, 24 * k, Ui.alpha(ink, 0.8f));
            clock = Ui.numbers(Ui.displaySemibold, 80 * k, ink);
            label = Ui.text(Ui.semibold, 24 * k, ink);
            rowLabel = Ui.text(Ui.semibold, 26 * k, ink);
            title = Ui.text(Ui.semibold, 30 * k, ink);
            empty = Ui.text(Ui.medium, 24 * k, Ui.alpha(ink, 0.7f));
            hintPaint = Ui.text(Ui.medium, 22 * k, 0xFF8E8E93);
            rim.setStyle(Paint.Style.STROKE);
            setFocusable(true);
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) {
                    if (!inputsOpen) {
                        int n = side != null ? side.size() : tiles.length;
                        for (int i = 0; i < n; i++) out.add(i + 1);
                        return;
                    }
                    int first = firstRow();
                    for (int i = first; i < Math.min(inputs.size(), first + ROWS_VISIBLE); i++) out.add(ROW_IDS + i + 1);
                }
                @Override public String text(int id) {
                    if (id <= ROW_IDS && side != null) return id - 1 < side.size() ? side.get(id - 1).label : "";
                    if (id <= ROW_IDS) return id - 1 < tiles.length ? tiles[id - 1].label : "";
                    int i = id - ROW_IDS - 1;
                    if (inputs == null || i >= inputs.size()) return "";
                    return inputs.get(i).label + ", " + Ui.trf("{i} of {n}", "i", i + 1, "n", inputs.size());
                }
                @Override public boolean bounds(int id, Rect out) {
                    if (id <= ROW_IDS && side != null) {
                        int i = id - 1;
                        if (inputsOpen || i >= side.size()) return false;
                        float y = sideTop(i);
                        out.set(Math.round(SIDE_PAD * k), Math.round(y), Math.round(getWidth() - SIDE_PAD * k),
                                Math.round(y + sideH(i)));
                        return true;
                    }
                    if (id <= ROW_IDS) {
                        int i = id - 1;
                        if (inputsOpen || i >= tiles.length) return false;
                        out.set(Math.round(tileX[i]), Math.round(tileY[i]), Math.round(tileX[i] + tileW),
                                Math.round(tileY[i] + tileH));
                        return true;
                    }
                    int i = id - ROW_IDS - 1;
                    if (!inputsOpen || i >= inputs.size()) return false;
                    float y = rowTop(i);
                    out.set(Math.round(inset * k), Math.round(y), Math.round(getWidth() - inset * k), Math.round(y + ROW_H * k));
                    return true;
                }
                @Override public int focused() {
                    return inputsOpen ? ROW_IDS + inputFocus + 1 : (side != null ? sideFocus : focus) + 1;
                }
                @Override public void focus(int id) {
                    if (id > ROW_IDS) inputFocus = id - ROW_IDS - 1;
                    else if (side != null) sideFocus = id - 1;
                    else focus = id - 1;
                    aimLens(false);
                    invalidate();
                }
                @Override public void click(int id) { select(); }
            });
            atop.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            for (Tile t : tiles) Symbols.warm(c, t.symbol, SYMBOL * k);
            if (side != null) for (HomeApps.Item it : side) Symbols.warm(c, it.symbol, SYMBOL * k);
            Symbols.warm(c, "tv", SYMBOL * k);
            show.target = 1;
        }

        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            return spoken;
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            card.set(0, 0, w, h);
            // ~95% opaque: with no blur behind it, the glass has to hold the text on its own, over a white or black screen.
            // The sidebar fully: it covers the home screen's own text, which would show through as noise.
            int o = side != null ? 0xFF000000 : 0;
            tint = night ? new LinearGradient(0, 0, 0, h, 0xF22C2C30 | o, 0xF71C1C1E | o, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, h, 0xF7FBFBFD | o, 0xF2EBEBF0 | o, Shader.TileMode.CLAMP);
            sheen = new LinearGradient(0, 0, 0, h * 0.42f, night ? 0x1FFFFFFF : 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            cardRim = new LinearGradient(0, 0, w, h, new int[] {0xB3FFFFFF, 0x1AFFFFFF, 0x14FFFFFF, 0x66FFFFFF},
                    new float[] {0, 0.35f, 0.65f, 1}, Shader.TileMode.CLAMP);
            tileRim = night ? new LinearGradient(0, 0, 0, tileH, 0x40FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, tileH, 0x0A000000, 0x1A000000, Shader.TileMode.CLAMP);
            lens = night ? new LinearGradient(0, 0, 0, tileH, 0xFFFFFFFF, 0xFFE4E8F4, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, tileH, 0xFF3A3A3C, 0xFF1D1D1F, Shader.TileMode.CLAMP);
            // Faint light resting in the glass: it doesn't drift, the panel draws only while something moves.
            float r = 380 * k;
            glows = new RadialGradient[] {
                new RadialGradient(0, 0, r, 0x267A4DFF, 0x007A4DFF, Shader.TileMode.CLAMP),
                new RadialGradient(0, 0, r, 0x2022C4FF, 0x0022C4FF, Shader.TileMode.CLAMP),
                new RadialGradient(0, 0, r * 0.85f, 0x1AFF3D8B, 0x00FF3D8B, Shader.TileMode.CLAMP),
            };
            float pad = (hub ? HUB_PAD : PAD) * k, gap = (hub ? HUB_GAP : GAP) * k;
            tileW = (w - 2 * pad - (cols - 1) * gap) / cols;
            float room = tileW - 44 * k;
            for (int i = 0; i < tiles.length; i++) {
                float size = (hub ? 30 : 24) * k;
                label.setTextSize(size);
                labelSize[i] = size * Math.min(1, room / Math.max(1, label.measureText(tiles[i].label)));
            }
            for (int i = 0; i < tiles.length; i++) {
                tileX[i] = pad + (i % cols) * (tileW + gap);
                tileY[i] = gridTop * k + (i / cols) * (tileH + gap);
            }
            aimLens(true);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            tick.run();
            spoken.readAgain();
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            removeCallbacks(frame);
            super.onDetachedFromWindow();
        }

        void close() {
            if (closing) return;
            closing = true;
            show.target = 0;
            invalidate();
        }

        private boolean admin() {
            return getIntent().getBooleanExtra(EXTRA_ADMIN, false);
        }

        /** "Online · Wi-Fi", "Online · Ethernet" or "Offline": the connection, as Android judges it (validated). */
        private String status(Context c) {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return Ui.tr("Offline");
            String how = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : Ui.tr("Wi-Fi");
            return Ui.trf("Online · {how}", "how", how);
        }

        /** Points the lens at the focused tile, or the focused row of the Inputs list. */
        private void aimLens(boolean snap) {
            float x, y, w, h;
            if (inputsOpen) {
                x = inset * k;
                y = rowTop(inputFocus);
                w = getWidth() - 2 * inset * k;
                h = ROW_H * k;
            } else if (side != null) {
                x = SIDE_PAD * k;
                y = sideTop(sideFocus);
                w = getWidth() - 2 * SIDE_PAD * k;
                h = sideH(sideFocus);
            } else {
                x = tileX[focus];
                y = tileY[focus];
                w = tileW;
                h = tileH;
            }
            if (snap) {
                lensX.snap(x);
                lensY.snap(y);
                lensW.snap(w);
                lensH.snap(h);
            } else {
                lensX.target = x;
                lensY.target = y;
                lensW.target = w;
                lensH.target = h;
            }
        }

        /** Where the popover's focus starts: the Live TV button's input, else the first one with a signal. */
        private int startRow(List<HomeApps.Item> list) {
            String live = Prefs.liveButton();
            for (int i = 0; i < list.size(); i++) if (!live.isEmpty() && live.equals(list.get(i).inputId)) return i;
            for (int i = 0; i < list.size(); i++) if (list.get(i).connected && list.get(i).kind != HomeApps.LIVE_TV) return i;
            return 0;
        }

        /** The first Inputs row on screen: the list scrolls to keep the focused one in view. */
        private int firstRow() {
            return Math.max(0, Math.min(inputFocus - ROWS_VISIBLE + 1, inputs == null ? 0 : inputs.size() - ROWS_VISIBLE));
        }

        private float rowTop(int i) {
            return rowsTop * k + (i - firstRow()) * (ROW_H + ROW_GAP) * k;
        }

        /** A sidebar row's top: the profile first, the others under it, the store at the bottom on its own. */
        private float sideTop(int i) {
            if (i == 0) return SIDE_PAD * k;
            if (SIDE_STORE.equals(side.get(i).key)) return getHeight() - (SIDE_PAD + ROW_H) * k;
            return (SIDE_PAD + PROFILE_H + 18 + (i - 1) * (ROW_H + ROW_GAP)) * k;
        }

        private float sideH(int i) {
            return (i == 0 ? PROFILE_H : ROW_H) * k;
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            boolean moving = show.step(dt);
            moving |= lensX.step(dt) | lensY.step(dt) | lensW.step(dt) | lensH.step(dt);
            moving |= press.step(dt) | page.step(dt);
            if (closing && show.value < 0.03f) {
                finish();
                return;
            }
            if (!inputsOpen && page.value < 0.01f) inputs = null;

            float s = Math.max(0, show.value);
            c.save();
            if (inputsOnly) c.translate(0, -(1 - s) * 24 * k);   // the popover drops in from its button
            else c.translate(-(1 - s) * (getWidth() + MARGIN * k), 0);
            // An offscreen layer costs a full-size pass on a TV's GPU each frame: only while the panel fades. The sidebar
            // only slides (40% janky frames on a MiTV-MSSP3 with the layers).
            int alpha = side != null ? 255 : Math.round(255 * Math.min(1, s * 1.4f));
            if (alpha < 255) c.saveLayerAlpha(card, alpha);
            else c.save();
            drawGlass(c);
            if (!inputsOnly && side == null && (!hub || !inputsOpen)) drawHeader(c);
            float p = page.value;
            if (p < 0.999f) {
                if (side != null) drawSide(c, 1 - p);
                else drawTiles(c, 1 - p);
            }
            if (inputs != null && p > 0.001f) drawInputs(c, p);
            c.restore();
            c.restore();
            if (moving) {
                postOnAnimation(frame);
            } else {
                lastFrame = 0;   // at rest: the next move starts from one frame, not from the time spent still
            }
        }

        /** Tinted glass with faint light inside, a sheen at the top and a bright rim. */
        private void drawGlass(Canvas c) {
            float r = cardR * k;
            if (hub) {   // the whole screen: the page's own dark ground, no card edge at the screen's
                fill.setShader(null);
                fill.setColor(0xFF0E0E10);
                c.drawRect(card, fill);
                return;
            }
            if (side != null) {   // opaque glass: the light can go straight on it, no layer to keep it inside
                drawOpaqueGlass(c, r);
                return;
            }
            c.saveLayer(card, layer);
            fill.setAlpha(255);   // a shader is still scaled by the paint's alpha, which the last tile left low
            fill.setShader(tint);
            c.drawRoundRect(card, r, r, fill);
            fill.setShader(null);
            float w = card.width(), h = card.height();
            for (int i = 0; i < glows.length; i++) {
                c.save();
                c.translate(glowX[i] * w, glowY[i] * h);
                atop.setShader(glows[i]);
                c.drawCircle(0, 0, 400 * k, atop);
                c.restore();
            }
            atop.setShader(null);
            c.restore();
            fill.setShader(sheen);
            c.drawRoundRect(card, r, r, fill);
            fill.setShader(null);
            rim.setStrokeWidth(1.6f * k);
            rim.setShader(cardRim);
            rect.set(card);
            rect.inset(0.8f * k, 0.8f * k);
            c.drawRoundRect(rect, r, r, rim);
            rim.setShader(null);
        }

        /** The sidebar's glass: the same tint, sheen and rim, without the light inside (it would need a layer). */
        private void drawOpaqueGlass(Canvas c, float r) {
            fill.setAlpha(255);
            fill.setShader(tint);
            c.drawRoundRect(card, r, r, fill);
            fill.setShader(sheen);
            c.drawRoundRect(card, r, r, fill);
            fill.setShader(null);
            rim.setStrokeWidth(1.6f * k);
            rim.setShader(cardRim);
            rect.set(card);
            rect.inset(0.8f * k, 0.8f * k);
            c.drawRoundRect(rect, r, r, rim);
            rim.setShader(null);
        }

        /** The settings page: its name, large, and one quiet line of how the TV is connected. */
        private void drawHubHeader(Canvas c) {
            title.setTextSize(56 * k);
            String name = admin() ? "Free My TV" : Ui.tr("Settings");
            c.drawText(name, HUB_PAD * k - Ui.leftBearing(title, name), 170 * k, title);
            title.setTextSize(30 * k);
            c.drawText(status, HUB_PAD * k, 222 * k, empty);
        }

        private void drawHeader(Canvas c) {
            if (hub) {
                drawHubHeader(c);
                return;
            }
            c.drawText(dateText, PAD * k, (PAD + 24) * k, date);
            c.drawText(clockText, PAD * k - clockBearing, (PAD + 116) * k, clock);
        }

        /** The grid: glass tiles, the lens under the focused one, then each tile's colored chip and label. */
        private void drawTiles(Canvas c, float a) {
            if (hub) {
                drawHubTiles(c, a);
                return;
            }
            int saved = c.saveLayerAlpha(card, Math.round(255 * a));
            c.translate(0, (1 - a) * -24 * k);
            float r = TILE_R * k;
            for (int i = 0; i < tiles.length; i++) {
                rect.set(tileX[i], tileY[i], tileX[i] + tileW, tileY[i] + tileH);
                fill.setColor(tileFill);
                c.drawRoundRect(rect, r, r, fill);
                drawRim(c, rect, r);
            }
            if (!inputsOpen) drawLens(c, r);
            for (int i = 0; i < tiles.length; i++) {
                float on = nearness(tileX[i], tileY[i], tileW);
                float lift = on * (1 - 0.04f * press.value);
                float cx = tileX[i] + tileW / 2, cy = tileY[i] + tileH / 2;
                c.save();
                c.scale(1 + 0.03f * lift, 1 + 0.03f * lift, cx, cy);
                float chipX = tileX[i] + (22 + CHIP_R) * k, chipY = tileY[i] + (22 + CHIP_R) * k;
                drawChip(c, chipX, chipY, tiles[i].color, tiles[i].symbol, on);
                label.setColor(mix(ink, focusInk, on));
                label.setTextSize(labelSize[i]);
                c.drawText(tiles[i].label, tileX[i] + 22 * k, tileY[i] + tileH - 24 * k, label);
                c.restore();
            }
            c.restoreToCount(saved);
        }

        /**
         * The settings page's tiles, in the quiet way of a modern app's settings (Linear, Vercel): a dark card with a
         * hairline border, a white line icon, its name and one grey line of what's inside. The focused one lightens,
         * takes a white border and lifts a little; no colour but what means something.
         */
        private void drawHubTiles(Canvas c, float a) {
            int saved = a < 0.999f ? c.saveLayerAlpha(card, Math.round(255 * a)) : c.save();
            float r = 22 * k;
            for (int i = 0; i < tiles.length; i++) {
                float on = nearness(tileX[i], tileY[i], tileW), lift = on * (1 - 0.03f * press.value);
                rect.set(tileX[i], tileY[i], tileX[i] + tileW, tileY[i] + tileH);
                c.save();
                c.scale(1 + 0.025f * lift, 1 + 0.025f * lift, rect.centerX(), rect.centerY());
                fill.setShader(null);
                fill.setColor(mix(0xFF161618, 0xFF222226, on));
                c.drawRoundRect(rect, r, r, fill);
                rim.setShader(null);
                rim.setColor(mix(0xFF2A2A2E, 0xFFF5F5F7, on));
                rim.setStrokeWidth((1.2f + 1.3f * on) * k);
                c.drawRoundRect(rect, r, r, rim);
                float icon = 34 * k, ix = rect.left + 30 * k, iy = rect.top + 30 * k;
                Symbols.draw(getContext(), c, tiles[i].symbol, ix + icon / 2, iy + icon / 2,
                        Symbols.pointSizeForHeight(getContext(), tiles[i].symbol, icon), mix(0x99F5F5F7, 0xFFF5F5F7, on));
                boolean hinted = tiles[i].hint != null;
                label.setColor(0xFFF5F5F7);
                label.setTextSize(labelSize[i]);
                c.drawText(tiles[i].label, rect.left + 30 * k, rect.bottom - (hinted ? 62 : 30) * k, label);
                if (hinted) c.drawText(tiles[i].hint, rect.left + 30 * k, rect.bottom - 28 * k, hintPaint);
                c.restore();
            }
            c.restoreToCount(saved);
        }

        /** The Inputs list: a title and one glass row per input, the lens on the focused one. */
        private void drawInputs(Canvas c, float a) {
            int saved = c.saveLayerAlpha(card, Math.round(255 * a));
            c.translate(0, (1 - a) * 24 * k);
            if (!inputsOnly) c.drawText(inputsTitle, PAD * k, (rowsTop - 28) * k, title);
            if (inputs.isEmpty()) {
                c.drawText(noInputs, inset * k, (rowsTop + 42) * k, empty);
                c.restoreToCount(saved);
                return;
            }
            float r = 24 * k, w = getWidth() - 2 * inset * k;
            int first = firstRow();
            int last = Math.min(inputs.size(), first + ROWS_VISIBLE);
            for (int i = first; i < last; i++) {
                float y = rowTop(i);
                rect.set(inset * k, y, inset * k + w, y + ROW_H * k);
                fill.setColor(tileFill);
                c.drawRoundRect(rect, r, r, fill);
                drawRim(c, rect, r);
            }
            if (inputsOpen) drawLens(c, r);
            for (int i = first; i < last; i++) {
                HomeApps.Item it = inputs.get(i);
                float y = rowTop(i);
                float on = nearness(inset * k, y, w);
                float dim = it.connected ? 1 : 0.7f;   // still 4.5:1 on the light glass
                drawChip(c, inset * k + 46 * k, y + ROW_H * k / 2, it.kind == HomeApps.LIVE_TV ? 0xFFFF9F0A : 0xFF30B0C7,
                        it.symbol != null ? it.symbol : "hdmi", on);
                rowLabel.setColor(Ui.alpha(mix(ink, focusInk, on), Math.max(dim, on)));
                c.drawText(it.label, inset * k + 92 * k, y + ROW_H * k / 2 + Ui.halfCap(rowLabel), rowLabel);
            }
            c.restoreToCount(saved);
        }

        /**
         * The sidebar: one glass row each, the lens on the focused one; the first row is who is watching (their avatar
         * and name), the store sits last under a thin line, quieter than the rest.
         */
        private void drawSide(Canvas c, float a) {
            int saved = a < 0.999f ? c.saveLayerAlpha(card, Math.round(255 * a)) : c.save();
            c.translate(0, (1 - a) * -24 * k);
            float r = 24 * k, x = SIDE_PAD * k, w = getWidth() - 2 * SIDE_PAD * k;
            if (!inputsOpen) drawLens(c, r);
            for (int i = 0; i < side.size(); i++) {
                HomeApps.Item it = side.get(i);
                float y = sideTop(i), h = sideH(i), cy = y + h / 2;
                float on = Math.max(0, 1 - Math.abs(lensY.value - y) / h);   // rows are stacked: by height, not width
                boolean store = SIDE_STORE.equals(it.key);
                if (store) {
                    rim.setStrokeWidth(1f * k);
                    rim.setColor(Ui.alpha(ink, 0.14f));
                    c.drawLine(x + 24 * k, y - SIDE_RULE * k / 2, x + w - 24 * k, y - SIDE_RULE * k / 2, rim);
                }
                float textX = x + 92 * k;
                if (i == 0) {
                    float ar = SIDE_AVATAR * k / 2, ax = x + 16 * k + ar;
                    if (avatar != null) {
                        rect.set(ax - ar, cy - ar, ax + ar, cy + ar);
                        float sx = rect.width() / avatar.getWidth(), sy = rect.height() / avatar.getHeight();
                        c.save();
                        c.translate(rect.left, rect.top);
                        c.scale(sx, sy);
                        fill.setAlpha(255);
                        fill.setShader(avatarShader);
                        c.drawCircle(avatar.getWidth() / 2f, avatar.getHeight() / 2f, avatar.getWidth() / 2f, fill);
                        fill.setShader(null);
                        c.restore();
                    } else {
                        drawChip(c, ax, cy, sideColors[i], it.symbol, on);
                    }
                    textX = ax + ar + 22 * k;
                    title.setColor(mix(ink, focusInk, on));
                    c.drawText(it.label, textX, cy + Ui.halfCap(title), title);
                    continue;
                }
                drawChip(c, x + 46 * k, cy, sideColors[i], it.symbol, on);
                rowLabel.setColor(Ui.alpha(mix(ink, focusInk, on), store ? Math.max(0.7f, on) : 1));
                c.drawText(it.label, textX, cy + Ui.halfCap(rowLabel), rowLabel);
            }
            c.restoreToCount(saved);
        }

        /** How much the lens covers this tile (1 = resting on it): the label and chip follow it as it flows. */
        private float nearness(float x, float y, float w) {
            float d = Math.abs(lensX.value - x) + Math.abs(lensY.value - y);
            return Math.max(0, 1 - d / (w * 0.6f));
        }

        private void drawRim(Canvas c, RectF r, float radius) {
            c.save();
            c.translate(r.left, r.top);
            rim.setStrokeWidth(1.2f * k);
            rim.setShader(tileRim);
            rect.set(0.6f * k, 0.6f * k, r.width() - 0.6f * k, r.height() - 0.6f * k);
            c.drawRoundRect(rect, radius, radius, rim);
            rim.setShader(null);
            c.restore();
        }

        /**
         * The focus lens: a bright drop of glass that flows to the focused tile, stretching along its way like a
         * liquid and squashing a little when OK is pressed, with a soft glow of the tile's color around it.
         */
        private void drawLens(Canvas c, float radius) {
            float vx = Math.abs(lensX.velocity), vy = Math.abs(lensY.velocity);
            float stretchX = Math.min(0.16f, vx / (9000 * k)), stretchY = Math.min(0.16f, vy / (9000 * k));
            float w = lensW.value * (1 + stretchX - stretchY * 0.5f) * (1 - 0.04f * press.value);
            float h = lensH.value * (1 + stretchY - stretchX * 0.5f) * (1 - 0.04f * press.value);
            float cx = lensX.value + lensW.value / 2, cy = lensY.value + lensH.value / 2;
            int accent = inputsOpen ? 0xFF30B0C7 : side != null ? sideColors[sideFocus] : tiles[focus].color;
            for (int i = 3; i >= 1; i--) {
                float g = i * 7 * k;
                rect.set(cx - w / 2 - g, cy - h / 2 - g, cx + w / 2 + g, cy + h / 2 + g);
                fill.setColor(Ui.alpha(accent, 0.10f));
                c.drawRoundRect(rect, radius + g, radius + g, fill);
            }
            rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            c.save();
            c.translate(0, rect.top);
            fill.setAlpha(255);
            fill.setShader(lens);
            rect.offset(0, -rect.top);
            c.drawRoundRect(rect, radius, radius, fill);
            fill.setShader(null);
            rim.setStrokeWidth(1.4f * k);
            rim.setColor(night ? 0xFFFFFFFF : 0x33000000);
            c.drawRoundRect(rect, radius, radius, rim);
            c.restore();
        }

        /** A circle in the tile's color (lighter at the top, like lit glass) with its white symbol. */
        private void drawChip(Canvas c, float cx, float cy, int color, String symbol, float on) {
            float r = CHIP_R * k;
            LinearGradient lit = null;
            for (int i = 0; i < chips && lit == null; i++) if (chipColor[i] == color) lit = chipShader[i];
            if (lit == null) {
                lit = new LinearGradient(0, -r, 0, r, mix(color, 0xFFFFFFFF, 0.28f), color, Shader.TileMode.CLAMP);
                if (chips < chipColor.length) {
                    chipColor[chips] = color;
                    chipShader[chips++] = lit;
                }
            }
            c.save();
            c.translate(cx, cy);
            fill.setAlpha(255);
            fill.setShader(lit);
            c.drawCircle(0, 0, r, fill);
            fill.setShader(null);
            c.restore();
            rim.setStrokeWidth(1f * k);
            rim.setColor(Ui.alpha(0xFFFFFFFF, 0.35f + 0.3f * on));
            c.drawCircle(cx, cy, r - 0.5f * k, rim);
            Symbols.draw(getContext(), c, symbol, cx, cy, SYMBOL * k, 0xFFFFFFFF);
        }

        /** OK on a sidebar row: the same as its button on the home screen; Inputs goes one level in. */
        private void selectSide(HomeApps.Item it) {
            Context c = getContext();
            if (SIDE_HOME.equals(it.key)) {
                close();
            } else if (SIDE_PROFILE.equals(it.key)) {
                startActivity(new Intent(c, WhoActivity.class));
                finish();
            } else if (it.kind == HomeApps.ANDROID_SETTINGS) {
                // The settings page over the whole screen: this same activity in that mode (it's singleTask: started
                // again, it would only take that as "close", the Settings button's way).
                setIntent(new Intent(c, QuickPanelActivity.class).putExtra(EXTRA_HUB, true));
                recreate();
            } else if (it.kind == HomeApps.RECENTS) {
                startActivity(new Intent(c, HomeActivity.class).putExtra(HomeActivity.EXTRA_RECENTS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else if (SIDE_APPS.equals(it.key)) {
                startActivity(new Intent(c, HomeActivity.class).putExtra(HomeActivity.EXTRA_ALL_APPS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else if (it.kind == HomeApps.INPUTS) {
                inputs = HomeApps.orderedInputs(c);
                inputsOpen = true;
                inputFocus = startRow(inputs);
                page.target = 1;
                aimLens(false);
                invalidate();
                spoken.focusMoved();
            } else if (HomeApps.open(QuickPanelActivity.this, it, this, 0, 0, getWidth(), getHeight())) {
                finish();
            }
        }

        boolean onKey(KeyEvent e) {
            if (closing) return true;
            int code = e.getKeyCode();
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (code) {
                case KeyEvent.KEYCODE_BACK:
                    if (!down && admin() && !inputsOpen) {   // the cards: back to the settings page
                        setIntent(new Intent(getContext(), QuickPanelActivity.class).putExtra(EXTRA_HUB, true));
                        recreate();
                        return true;
                    }
                    if (!down) {
                        if (inputsOpen && !inputsOnly) {
                            inputsOpen = false;
                            page.target = 0;
                            aimLens(false);
                            invalidate();
                            spoken.focusMoved();
                        } else {
                            close();
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_NOTIFICATION:
                    if (!down) close();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) move(code);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (down && e.getRepeatCount() == 0) {
                        press.target = 1;
                        invalidate();
                    } else if (!down) {
                        press.target = 0;
                        invalidate();
                        if (spoken.keyClick()) select();   // TalkBack's click may have come first for this press
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void move(int code) {
            if (side != null && !inputsOpen) {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {   // back to the home screen, as on Apple TV
                    close();
                    return;
                }
                int before = sideFocus;
                if (code == KeyEvent.KEYCODE_DPAD_UP) sideFocus = Math.max(0, sideFocus - 1);
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) sideFocus = Math.min(side.size() - 1, sideFocus + 1);
                if (sideFocus == before) return;
            } else if (inputsOpen) {
                int before = inputFocus;
                if (code == KeyEvent.KEYCODE_DPAD_UP) inputFocus = Math.max(0, inputFocus - 1);
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) inputFocus = Math.min(inputs.size() - 1, inputFocus + 1);
                if (inputFocus == before) return;
            } else {
                int before = focus;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && focus % cols > 0) focus--;
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && focus % cols < cols - 1 && focus + 1 < tiles.length) focus++;
                if (code == KeyEvent.KEYCODE_DPAD_UP && focus >= cols) focus -= cols;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus + cols < tiles.length) focus += cols;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus + cols >= tiles.length && focus / cols < (tiles.length - 1) / cols) {
                    focus = tiles.length - 1;   // down into a shorter last row: its last tile
                }
                if (focus == before) return;
            }
            click.move();
            aimLens(false);
            invalidate();
            spoken.focusMoved();
        }

        private void select() {
            click.open();
            if (inputsOpen) {
                if (inputs.isEmpty()) return;
                if (HomeApps.open(QuickPanelActivity.this, inputs.get(inputFocus), this, 0, 0, getWidth(), getHeight())) finish();
                return;
            }
            if (side != null) {
                selectSide(side.get(sideFocus));
                return;
            }
            if (hub && !admin() && ADMIN_SYMBOL.equals(tiles[focus].symbol)) {   // one level in, the same page
                setIntent(new Intent(getContext(), QuickPanelActivity.class).putExtra(EXTRA_HUB, true).putExtra(EXTRA_ADMIN, true));
                recreate();
                return;
            }
            if (focus == INPUTS && !admin()) {
                inputs = HomeApps.orderedInputs(getContext());
                inputsOpen = true;
                inputFocus = 0;
                page.target = 1;
                aimLens(false);
                invalidate();
                spoken.focusMoved();
                return;
            }
            // The quick panel goes once a tile opened; the settings page stays under it, for Back.
            if (open(tiles[focus].intents) && !hub) finish();
        }
    }
}
