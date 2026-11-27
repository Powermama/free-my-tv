package io.freemytv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * A trailer over the title's page, in YouTube's own embeddable player: Back (or its end) closes it and the page is
 * there again; the YouTube app keeps Back for itself. Its own process (:trailer), ended when it closes, so the web
 * view's memory goes back to the TV at once and a crash here never touches the home screen. A trailer its studio
 * keeps off other sites opens in the YouTube app instead. Until it really plays, a black cover with its title and a
 * breathing bar hides YouTube's own start screen; captions stay off; at its end it closes before YouTube's end screen.
 */
public class TrailerActivity extends Activity {
    static final String EXTRA_VIDEO = "io.freemytv.VIDEO", EXTRA_TITLE = "io.freemytv.TITLE";
    private static final String YOUTUBE = "com.google.android.youtube.tv";

    private WebView web;
    private String video;
    private View cover;
    private ObjectAnimator breathing;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        video = getIntent().getStringExtra(EXTRA_VIDEO);
        if (video == null || !video.matches("[A-Za-z0-9_-]{6,20}")) {   // YouTube's ids only: it goes into the page
            finish();
            return;
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // it plays at once: OK on "Trailer" was the gesture
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(cover(getIntent().getStringExtra(EXTRA_TITLE)),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // The page names a site of its own as its origin: YouTube's player refuses to play without one (error 153).
        web.loadDataWithBaseURL("https://freemytv.io/", page(video), "text/html", "utf-8", null);
    }

    /** Black, the title at the lower left and a bar that breathes (its alpha: drawn once), until the trailer plays. */
    private View cover(String title) {
        float k = getResources().getDisplayMetrics().widthPixels / 1920f;
        Ui.init(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.BOTTOM);
        c.setBackgroundColor(Color.BLACK);
        int pad = Math.round(80 * k);
        c.setPadding(pad, pad, pad, Math.round(96 * k));
        TextView t = WhoActivity.text(this, k, 56, Ui.displaySemibold, WhoActivity.INK);
        t.setText(title == null ? "" : title);
        c.addView(t, WhoActivity.wrap(0, 0, 0, Math.round(28 * k)));
        View bar = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x4DF5F5F7);
        d.setCornerRadius(4 * k);
        bar.setBackground(d);
        c.addView(bar, new LinearLayout.LayoutParams(Math.round(360 * k), Math.round(8 * k)));
        breathing = ObjectAnimator.ofFloat(bar, View.ALPHA, 1f, 0.3f);
        breathing.setDuration(800);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.start();
        cover = c;
        return c;
    }

    private static String page(String id) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>"
                + "</head><body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{videoId:'" + id + "',"
                + "playerVars:{autoplay:1,controls:0,rel:0,modestbranding:1,playsinline:1,fs:0,iv_load_policy:3,"
                + "cc_load_policy:0,disablekb:1},"
                + "events:{onReady:function(e){try{e.target.unloadModule('captions')}catch(x){}e.target.playVideo()},"
                + "onStateChange:function(e){if(e.data===1)Android.playing();if(e.data===0)Android.ended()},"
                + "onError:function(e){Android.failed(e.data)}}})}"
                + "function toggle(){if(!player)return;player.getPlayerState()===1?player.pauseVideo():player.playVideo()}"
                + "</script></body></html>";
    }

    /** What the page says: it ended, or it can't play here. */
    private final class Bridge {
        @JavascriptInterface public void playing() {
            runOnUiThread(() -> {
                if (cover == null || cover.getVisibility() != View.VISIBLE) return;
                if (breathing != null) breathing.cancel();
                cover.animate().alpha(0f).setDuration(300).withEndAction(() -> cover.setVisibility(View.GONE)).start();
            });
        }

        @JavascriptInterface public void ended() {
            runOnUiThread(TrailerActivity.this::finish);
        }

        @JavascriptInterface public void failed(int code) {
            runOnUiThread(() -> {
                AppLog.w("trailer: the embedded player said " + code + ", opening YouTube");
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + video))
                            .setPackage(YOUTUBE));
                } catch (ActivityNotFoundException | SecurityException e) {
                    AppLog.w("trailer: " + e.getMessage());
                }
                finish();
            });
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (e.getAction() == KeyEvent.ACTION_UP && web != null) web.evaluateJavascript("toggle()", null);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override protected void onDestroy() {
        if (breathing != null) breathing.cancel();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
        // Nothing else lives in :trailer: ending it hands the web view's memory back now, not when the system wants it.
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
