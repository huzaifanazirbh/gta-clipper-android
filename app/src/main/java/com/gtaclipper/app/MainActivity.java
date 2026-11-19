package com.gtaclipper.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GTA Clipper for Android.
 * Opens the online GTA Clipper (hosted on Hugging Face) full screen, with video upload from the
 * gallery, clip downloads into Downloads/GTA Clipper, fullscreen video and a friendly offline screen.
 */
public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 1001;
    private static final int STORAGE_REQUEST = 1002;
    private static final String PREFS = "gta_clipper";
    private static final String KEY_URL = "server_url";
    private static final int BG = Color.parseColor("#08050F");
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private FrameLayout root;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;
    private String appUrl = "";
    private String appHost = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // clipping can take a few minutes

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);

        setupWebView();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        appUrl = normalize(prefs.getString(KEY_URL, BuildConfig.SPACE_URL));
        if (appUrl.isEmpty()) {
            askForUrl(true);
        } else {
            openApp();
        }
    }

    // ------------------------------------------------------------------ web view
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflinePage(String.valueOf(error.getDescription()));
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("video/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose a video"), FILE_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    toast("No app found to pick videos.");
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(MATCH, MATCH));
                web.setVisibility(View.GONE);
                toggleImmersive(true);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                download(url, contentDisposition, mimeType));
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String url = uri.toString();

        if (scheme.equals("gtaclipper")) {               // buttons on the offline page
            if ("settings".equals(uri.getHost())) {
                askForUrl(false);
            } else {
                openApp();
            }
            return true;
        }
        if (scheme.equals("blob") || scheme.equals("data")) {
            toast("Use the ⬇️ Download all clips list to save clips.");
            return true;
        }
        if (url.contains("/file=") || isDownloadable(uri.getLastPathSegment())) {
            download(url, null, null);
            return true;
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (host.equals(appHost) || host.endsWith(".hf.space") || host.endsWith("huggingface.co")) {
            return false;                                    // stay inside the app
        }
        try {                                                // other links open in the browser
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
    }

    private static boolean isDownloadable(String lastSegment) {
        if (lastSegment == null) return false;
        String s = lastSegment.toLowerCase(Locale.ROOT);
        return s.endsWith(".mp4") || s.endsWith(".mov") || s.endsWith(".zip") || s.endsWith(".json") || s.endsWith(".txt");
    }

    // ------------------------------------------------------------------ downloads
    private void download(String url, String contentDisposition, String mimeType) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast("Use the ⬇️ Download all clips list to save clips.");
            return;
        }
        try {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("Cookie", cookies);
            req.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
            req.setTitle(name);
            req.setDescription("GTA Clipper");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            boolean publicDir = Build.VERSION.SDK_INT >= 29
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (publicDir) {
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GTA Clipper/" + name);
            } else {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST);
                req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            toast("Saving " + name + " to Downloads/GTA Clipper");
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ server address
    private void openApp() {
        if (appUrl.isEmpty()) {
            askForUrl(true);
            return;
        }
        Uri u = Uri.parse(appUrl);
        appHost = u.getHost() == null ? "" : u.getHost();
        if (!isOnline()) {
            showOfflinePage("No internet connection");
            return;
        }
        web.loadUrl(appUrl);
    }

    /** Accepts "https://huggingface.co/spaces/name/app" and turns it into the direct "https://name-app.hf.space". */
    static String normalize(String in) {
        if (in == null) return "";
        String url = in.trim();
        if (url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        Matcher m = Pattern.compile("huggingface\\.co/spaces/([^/?#]+)/([^/?#]+)").matcher(url);
        if (m.find()) {
            String sub = (m.group(1) + "-" + m.group(2)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
            url = "https://" + sub + ".hf.space";
        }
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private void askForUrl(boolean firstTime) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://huggingface.co/spaces/yourname/gta-clipper");
        input.setText(appUrl);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("GTA Clipper server link")
                .setMessage("Paste the link of your GTA Clipper on Hugging Face.")
                .setView(box)
                .setCancelable(!firstTime)
                .setPositiveButton("Open", (d, w) -> {
                    String url = normalize(input.getText().toString());
                    if (url.isEmpty()) {
                        askForUrl(firstTime);
                        return;
                    }
                    appUrl = url;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
                    openApp();
                });
        if (firstTime) {
            b.setNegativeButton("Exit", (d, w) -> finish());
        } else {
            b.setNegativeButton("Cancel", null);
        }
        b.show();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private void showOfflinePage(String reason) {
        String safe = reason == null ? "" : reason.replace("<", "&lt;").replace(">", "&gt;");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:radial-gradient(600px 400px at 80% 0%,rgba(255,45,149,.25),transparent),#08050f;"
                + "font-family:sans-serif;color:#f4f1ff;text-align:center;padding:24px;box-sizing:border-box}"
                + "h1{font-size:44px;margin:0 0 6px;letter-spacing:1px}h1 span{color:#ff8a3d}"
                + "p{color:#a59cc4;line-height:1.5}.sun{width:110px;height:110px;border-radius:50%;margin:0 auto 22px;"
                + "background:linear-gradient(#ffe259,#ff8a3d,#ff2d95);box-shadow:0 0 40px #ff2d95}"
                + "a{display:block;margin:12px auto;max-width:280px;padding:15px;border-radius:14px;text-decoration:none;font-weight:bold}"
                + ".go{background:linear-gradient(90deg,#ff2d95,#ff8a3d);color:#fff}"
                + ".alt{border:1px solid rgba(255,255,255,.2);color:#f4f1ff}small{color:#6f6690}</style></head><body><div>"
                + "<div class='sun'></div><h1>GTA <span>CLIPPER</span></h1>"
                + "<p>Can't reach the clipper right now.<br>If it was sleeping, it takes about a minute to wake up.</p>"
                + "<a class='go' href='gtaclipper://retry'>↻ Try again</a>"
                + "<a class='alt' href='gtaclipper://settings'>⚙ Change server link</a>"
                + "<small>" + safe + "</small></div></body></html>";
        web.loadDataWithBaseURL("https://gtaclipper.local/", html, "text/html", "utf-8", null);
    }

    // ------------------------------------------------------------------ activity plumbing
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        toggleImmersive(false);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @SuppressWarnings("deprecation")
    private void toggleImmersive(boolean on) {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(on
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : 0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
