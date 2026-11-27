package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * "Who's watching?": the family's avatars in a row, the last one picked already focused, so one OK starts the evening.
 * At a real power-on the home screen opens it (Profiles.shouldAsk); the avatar in the home screen's corner opens it
 * any time. "Manage profiles" turns the row into editing (ProfileActivity), one level deeper, as Netflix does.
 * Plain Android views, drawn at 1080p and scaled to the screen (k).
 */
public class WhoActivity extends Activity {
    static final int INK = 0xFFF5F5F7, DIM = 0x99F5F5F7, PILL = 0x1FFFFFFF, DARK = 0xFF1C1C1E;
    private static final int AVATAR = 200, GAP = 56;

    private float k;
    private boolean manage;
    private TextView title, action;
    private LinearLayout row;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        k = getResources().getDisplayMetrics().widthPixels / 1920f;
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackground(background());
        title = text(this, k, 56, Ui.displaySemibold, INK);
        column.addView(title, wrap(0, 0, 0, Math.round(72 * k)));
        row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        column.setClipChildren(false);
        column.addView(row, wrap(0, 0, 0, Math.round(88 * k)));
        action = text(this, k, 26, Ui.medium, INK);
        pill(action, k);
        action.setOnClickListener(v -> setManage(!manage));
        column.addView(action, wrap(0, 0, 0, 0));
        setContentView(column);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private void setManage(boolean on) {
        manage = on;
        build();
        if (!on) action.requestFocus();
    }

    private void build() {
        title.setText(manage ? Ui.tr("Manage profiles") : Ui.tr("Who's watching?"));
        action.setText(manage ? Ui.tr("Done") : Ui.tr("Manage profiles"));
        row.removeAllViews();
        List<Profiles.Profile> list = Profiles.all(this);
        Profiles.Profile active = Profiles.active(this);
        View focus = null;
        for (Profiles.Profile p : list) {
            View cell = cell(p.name, Profiles.avatar(this, p.avatar, Math.round(AVATAR * k)), manage ? "edit" : null);
            cell.setOnClickListener(v -> {
                if (manage) {
                    startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_ID, p.id));
                } else {
                    Profiles.pick(this, p.id);
                    AppLog.w("profiles: picked");
                    finish();
                }
            });
            row.addView(cell);
            if (active != null && active.id.equals(p.id)) focus = cell;
        }
        if (list.size() < Profiles.MAX) {
            View add = cell(Ui.tr("New"), null, "add");
            add.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
            row.addView(add);
            if (focus == null) focus = add;
        }
        action.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (focus != null) focus.requestFocus();
    }

    /** An avatar (or, without a picture, a dashed circle) with its name under it; focus grows it and rings it. */
    private View cell(String name, Bitmap picture, String symbol) {
        int size = Math.round(AVATAR * k);
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClipChildren(false);
        FrameLayout circle = new FrameLayout(this);
        circle.setClipChildren(false);
        if (picture != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(picture);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            round(image);
            if (symbol != null) image.setAlpha(0.45f);
            circle.addView(image, new FrameLayout.LayoutParams(size, size));
        } else {
            GradientDrawable dashed = new GradientDrawable();
            dashed.setShape(GradientDrawable.OVAL);
            dashed.setStroke(Math.round(3 * k), DIM, 12 * k, 10 * k);
            circle.setBackground(dashed);
        }
        if (symbol != null) circle.addView(new SymbolView(this, symbol, 64 * k), new FrameLayout.LayoutParams(size, size));
        View ring = ring(this, k, size);
        circle.addView(ring, ring.getLayoutParams());
        cell.addView(circle, new LinearLayout.LayoutParams(size, size));
        TextView label = text(this, k, 30, Ui.medium, DIM);
        label.setText(name);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size + Math.round(40 * k), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(28 * k);
        cell.addView(label, lp);
        cell.setOnFocusChangeListener((v, on) -> {
            circle.animate().scaleX(on ? 1.1f : 1).scaleY(on ? 1.1f : 1).setDuration(160).start();
            ring.animate().alpha(on ? 1 : 0).setDuration(160).start();
            label.setTextColor(on ? INK : DIM);
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = cp.rightMargin = Math.round(GAP / 2f * k);
        cell.setLayoutParams(cp);
        return cell;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            if (manage) {
                setManage(false);
            } else {
                // Back keeps whoever watched last, so this screen never holds anyone up.
                Profiles.Profile p = Profiles.active(this);
                if (p != null) Profiles.pick(this, p.id);
                finish();
            }
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // The pieces both profile screens are made of: the same dark background, text, pill and focus ring.

    static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF1C1C1E, 0xFF0B0B0D});
    }

    static TextView text(Context c, float k, float px, android.graphics.Typeface face, int color) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * k);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A focusable pill: translucent at rest, white with dark text when focused. */
    static void pill(TextView t, float k) {
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER);
        int h = Math.round(28 * k), v = Math.round(16 * k);
        t.setPadding(h, v, h, v);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(100 * k);
        bg.setColor(PILL);
        t.setBackground(bg);
        t.setOnFocusChangeListener((view, on) -> {
            bg.setColor(on ? INK : PILL);
            t.setTextColor(on ? DARK : INK);
            view.animate().scaleX(on ? 1.06f : 1).scaleY(on ? 1.06f : 1).setDuration(160).start();
        });
    }

    /** The focus ring: white, just outside a circle of `size` px with a small gap, hidden until focused. */
    static View ring(Context c, float k, int size) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(Math.round(5 * k), INK);
        View ring = new View(c);
        ring.setBackground(d);
        ring.setAlpha(0);
        int outer = size + Math.round(22 * k);
        ring.setLayoutParams(new FrameLayout.LayoutParams(outer, outer, Gravity.CENTER));
        return ring;
    }

    static void round(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
    }

    static LinearLayout.LayoutParams wrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(left, top, right, bottom);
        return lp;
    }

    /** One of the bundled symbols, centered, in the text color. */
    static final class SymbolView extends View {
        private final String name;
        private final float height;

        SymbolView(Context c, String name, float height) {
            super(c);
            this.name = name;
            this.height = height;
        }

        @Override protected void onDraw(Canvas c) {
            Symbols.draw(getContext(), c, name, getWidth() / 2f, getHeight() / 2f,
                    Symbols.pointSizeForHeight(getContext(), name, height), INK);
        }
    }
}
