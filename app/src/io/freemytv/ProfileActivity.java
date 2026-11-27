package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * One profile, opened from "Who's watching?", one thing at a time. A new one goes name, then avatar, then a last look
 * with Save. An existing one opens on that last look: OK on the avatar or the name goes one step in to change it.
 * Back goes one step out, and leaves without saving from the first.
 */
public class ProfileActivity extends Activity {
    static final String EXTRA_ID = "io.freemytv.PROFILE";
    private static final int COLUMNS = 10, AVATAR = 120, GAP = 28, BIG = 240, NAME_MAX = 20;
    private static final int NAME = 0, AVATARS = 1, SUMMARY = 2;

    private float k;
    private String id, name = "", avatar;
    private int step;
    private FrameLayout screen;
    private EditText field;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        k = getResources().getDisplayMetrics().widthPixels / 1920f;
        id = getIntent().getStringExtra(EXTRA_ID);
        for (Profiles.Profile p : Profiles.all(this)) {
            if (p.id.equals(id)) {
                name = p.name;
                avatar = p.avatar;
            }
        }
        if (avatar == null) id = null;   // deleted meanwhile: a new one
        screen = new FrameLayout(this);
        screen.setBackground(WhoActivity.background());
        setContentView(screen);
        show(id == null ? NAME : SUMMARY);
    }

    private void show(int next) {
        step = next;
        hideKeyboard();
        screen.removeAllViews();
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setClipChildren(false);
        screen.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (step == NAME) nameStep(column);
        else if (step == AVATARS) avatarStep(column);
        else summary(column);
    }

    private TextView title(LinearLayout column, String text) {
        TextView t = WhoActivity.text(this, k, 56, Ui.displaySemibold, WhoActivity.INK);
        t.setText(text);
        column.addView(t, WhoActivity.wrap(0, 0, 0, Math.round(64 * k)));
        return t;
    }

    private TextView button(LinearLayout parent, String text, View.OnClickListener click, int leftMargin) {
        TextView b = WhoActivity.text(this, k, 26, Ui.medium, WhoActivity.INK);
        b.setText(text);
        WhoActivity.pill(b, k);
        b.setOnClickListener(click);
        parent.addView(b, WhoActivity.wrap(leftMargin, 0, 0, 0));
        return b;
    }

    // MARK: - 1. The name: only the field and the TV's keyboard.

    private void nameStep(LinearLayout column) {
        title(column, Ui.tr("Name"));
        field = new EditText(this);
        field.setSingleLine(true);
        field.setText(name);
        field.setSelection(name.length());
        field.setHint(Ui.tr("Name"));
        field.setTypeface(Ui.medium);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 44 * k);
        field.setTextColor(WhoActivity.INK);
        field.setHintTextColor(WhoActivity.DIM);
        field.setGravity(Gravity.CENTER);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(NAME_MAX)});
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(100 * k);
        bg.setColor(WhoActivity.PILL);
        bg.setStroke(Math.round(3 * k), WhoActivity.DIM);
        field.setBackground(bg);
        int h = Math.round(40 * k), v = Math.round(22 * k);
        field.setPadding(h, v, h, v);
        field.setOnFocusChangeListener((view, on) -> bg.setStroke(Math.round(3 * k), on ? WhoActivity.INK : WhoActivity.DIM));
        field.setOnEditorActionListener((view, action, event) -> {
            nameDone();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(720 * k), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(48 * k);
        column.addView(field, lp);
        LinearLayout row = new LinearLayout(this);
        column.addView(row, WhoActivity.wrap(0, 0, 0, 0));
        button(row, Ui.tr("Next"), x -> nameDone(), 0);
        field.requestFocus();
        field.postDelayed(() -> {
            InputMethodManager im = getSystemService(InputMethodManager.class);
            if (im != null && step == NAME) im.showSoftInput(field, 0);
        }, 200);
    }

    private void nameDone() {
        String typed = field.getText().toString().trim();
        if (typed.isEmpty()) return;
        name = typed;
        show(id == null && avatar == null ? AVATARS : SUMMARY);
    }

    private void hideKeyboard() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null && field != null) im.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    // MARK: - 2. The avatar: only the pictures; OK on one takes it.

    private void avatarStep(LinearLayout column) {
        title(column, Ui.tr("Choose an avatar"));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        int size = Math.round(AVATAR * k), half = Math.round(GAP / 2f * k);
        View focus = null;
        for (String a : Profiles.avatars(this)) {
            FrameLayout cell = new FrameLayout(this);
            cell.setFocusable(true);
            cell.setClipChildren(false);
            ImageView image = new ImageView(this);
            image.setImageBitmap(Profiles.avatar(this, a, size));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            WhoActivity.round(image);
            cell.addView(image, new FrameLayout.LayoutParams(size, size));
            View ring = WhoActivity.ring(this, k, size);
            cell.addView(ring, ring.getLayoutParams());
            cell.setOnFocusChangeListener((view, on) -> {
                view.animate().scaleX(on ? 1.12f : 1).scaleY(on ? 1.12f : 1).setDuration(160).start();
                ring.animate().alpha(on ? 1 : 0).setDuration(160).start();
            });
            cell.setOnClickListener(view -> {
                avatar = a;
                show(SUMMARY);
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = lp.height = size;
            lp.setMargins(half, half, half, half);
            grid.addView(cell, lp);
            if (a.equals(avatar) || focus == null) focus = cell;
        }
        column.addView(grid, WhoActivity.wrap(0, 0, 0, 0));
        if (focus != null) focus.requestFocus();
    }

    // MARK: - 3. A last look: the avatar and the name as they will be, and Save.

    private void summary(LinearLayout column) {
        title(column, id == null ? Ui.tr("New profile") : Ui.tr("Edit profile"));
        int size = Math.round(BIG * k);
        FrameLayout big = new FrameLayout(this);
        big.setFocusable(true);
        big.setClipChildren(false);
        ImageView image = new ImageView(this);
        image.setImageBitmap(Profiles.avatar(this, avatar, size));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        WhoActivity.round(image);
        big.addView(image, new FrameLayout.LayoutParams(size, size));
        View ring = WhoActivity.ring(this, k, size);
        big.addView(ring, ring.getLayoutParams());
        big.setOnFocusChangeListener((view, on) -> {
            view.animate().scaleX(on ? 1.06f : 1).scaleY(on ? 1.06f : 1).setDuration(160).start();
            ring.animate().alpha(on ? 1 : 0).setDuration(160).start();
        });
        big.setOnClickListener(view -> show(AVATARS));
        column.addView(big, WhoActivity.wrap(0, 0, 0, Math.round(36 * k)));
        TextView who = WhoActivity.text(this, k, 40, Ui.displaySemibold, WhoActivity.INK);
        who.setText(name);
        WhoActivity.pill(who, k);
        who.setOnClickListener(view -> show(NAME));
        column.addView(who, WhoActivity.wrap(0, 0, 0, Math.round(64 * k)));
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        column.addView(row, WhoActivity.wrap(0, 0, 0, 0));
        TextView save = button(row, Ui.tr("Save"), view -> save(), 0);
        if (id != null) button(row, Ui.tr("Delete profile"), view -> delete(), Math.round(24 * k));
        save.requestFocus();
    }

    private void save() {
        String saved = Profiles.save(this, id, name, avatar);
        if (id == null) Profiles.pick(this, saved);   // a new profile is the one watching
        finish();
    }

    private void delete() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Ui.trf("Delete {name}?", "name", name))
                .setPositiveButton(Ui.tr("Delete"), (d, which) -> {
                    Profiles.remove(this, id);
                    finish();
                })
                .setNegativeButton(Ui.tr("Cancel"), null)
                .show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            if (step == SUMMARY || step == NAME && avatar == null) {
                finish();
            } else if (avatar == null) {
                show(NAME);   // a new one, before its avatar: back to its name
            } else {
                show(SUMMARY);   // a change one step in: back to the last look, as it was
            }
        }
        return true;
    }

    @Override public void finish() {
        hideKeyboard();
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
