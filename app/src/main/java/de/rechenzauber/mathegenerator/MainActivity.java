package de.rechenzauber.mathegenerator;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Base64;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int CREATE_PDF = 1;
    private static final int MAX_PDF_BYTES = 32 * 1024 * 1024;
    private final AtomicBoolean exporting = new AtomicBoolean();
    private WebView webView;
    private Path pendingPdf;
    private PrintJob printJob;

    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(36, 28, 54));
        if (Build.VERSION.SDK_INT >= 35) {
            frame.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        webView = new WebView(this);
        frame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        webView.addJavascriptInterface(new AndroidBridge(), "RechenzauberAndroid");
        webView.setWebViewClient(new LocalAssets());
        webView.setWebChromeClient(new WebChromeClient());

        if (state != null && state.getString("pendingPdf") != null) {
            pendingPdf = Paths.get(state.getString("pendingPdf"));
            exporting.set(true);
        }
        webView.loadUrl(START_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingPdf != null) state.putString("pendingPdf", pendingPdf.toString());
    }

    private static boolean isLocal(Uri url) {
        return "https".equals(url.getScheme()) && HOST.equals(url.getHost())
                && url.getPort() == -1 && url.getUserInfo() == null;
    }

    private final class LocalAssets extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            return !isLocal(url) || !"/index.html".equals(url.getPath());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (isLocal(url)) {
                String asset = url.getPath();
                String mime = "/index.html".equals(asset) ? "text/html"
                        : "/android.js".equals(asset) ? "text/javascript" : null;
                if (mime != null) {
                    try {
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(asset.substring(1)));
                    } catch (IOException ignored) {
                        // The same 404 response covers an unavailable bundled asset.
                    }
                }
            }
            return new WebResourceResponse("text/plain", "UTF-8", 404,
                    "Not Found", java.util.Collections.emptyMap(), null);
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void printWorksheet(String title) {
            runOnUiThread(() -> openPrintDialog(title));
        }

        @JavascriptInterface
        public void savePdf(String filename, String encodedPdf) {
            if (!exporting.compareAndSet(false, true)) return;
            Path temp = null;
            try {
                if (encodedPdf.length() > ((MAX_PDF_BYTES + 2) / 3) * 4) {
                    throw new IOException("PDF too large");
                }
                byte[] pdf = Base64.decode(encodedPdf, Base64.DEFAULT);
                if (pdf.length > MAX_PDF_BYTES) throw new IOException("PDF too large");
                temp = Files.createTempFile(getCacheDir().toPath(), "worksheet-", ".pdf");
                Files.write(temp, pdf);
                Path ready = temp;
                runOnUiThread(() -> choosePdfLocation(filename, ready));
            } catch (IOException | IllegalArgumentException error) {
                deleteTemporaryPdf(temp);
                exporting.set(false);
                runOnUiThread(() -> showMessage("Die PDF konnte nicht vorbereitet werden."));
            }
        }
    }

    private void openPrintDialog(String title) {
        if (isFinishing() || isDestroyed()) return;
        if (printJob != null && !printJob.isCompleted() && !printJob.isCancelled()
                && !printJob.isFailed()) return;

        String name = title == null || title.trim().isEmpty() ? getString(R.string.app_name) : title;
        PrintManager manager = getSystemService(PrintManager.class);
        if (manager == null) {
            resetPrintLayout();
            showMessage("Auf diesem Gerät ist kein Druckdienst verfügbar.");
            return;
        }
        try {
            PrintDocumentAdapter document = webView.createPrintDocumentAdapter(name);
            PrintDocumentAdapter adapter = new PrintDocumentAdapter() {
                @Override
                public void onStart() {
                    document.onStart();
                }

                @Override
                public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                     CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
                    document.onLayout(oldAttributes, newAttributes, cancel, callback, extras);
                }

                @Override
                public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                    CancellationSignal cancel, WriteResultCallback callback) {
                    document.onWrite(pages, destination, cancel, callback);
                }

                @Override
                public void onFinish() {
                    try {
                        document.onFinish();
                    } finally {
                        printJob = null;
                        resetPrintLayout();
                    }
                }
            };
            printJob = manager.print(name, adapter,
                    new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
        } catch (RuntimeException error) {
            resetPrintLayout();
            showMessage("Der Druckdialog konnte nicht geöffnet werden.");
        }
    }

    private void resetPrintLayout() {
        if (webView != null) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('afterprint'))", null);
        }
    }

    private void choosePdfLocation(String filename, Path source) {
        if (isFinishing() || isDestroyed()) {
            deleteTemporaryPdf(source);
            exporting.set(false);
            return;
        }
        pendingPdf = source;
        Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(picker, CREATE_PDF);
        } catch (ActivityNotFoundException error) {
            pendingPdf = null;
            deleteTemporaryPdf(source);
            exporting.set(false);
            showMessage("Auf diesem Gerät ist keine Dateiauswahl verfügbar.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_PDF || pendingPdf == null) return;
        Path source = pendingPdf;
        pendingPdf = null;
        Uri destination = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (destination == null) {
            deleteTemporaryPdf(source);
            exporting.set(false);
            return;
        }

        new Thread(() -> {
            String message;
            try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                if (stream == null) throw new IOException("Destination unavailable");
                Files.copy(source, stream);
                message = "PDF gespeichert.";
            } catch (IOException | SecurityException error) {
                message = "Die PDF konnte nicht gespeichert werden.";
            } finally {
                deleteTemporaryPdf(source);
                exporting.set(false);
            }
            String result = message;
            runOnUiThread(() -> showMessage(result));
        }, "rechenzauber-pdf").start();
    }

    private static void deleteTemporaryPdf(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The file is in Android's disposable app cache.
        }
    }

    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        ((FrameLayout) webView.getParent()).removeView(webView);
        webView.destroy();
        webView = null;
        super.onDestroy();
    }
}
