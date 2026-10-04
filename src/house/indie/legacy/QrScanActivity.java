package house.indie.legacy;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public final class QrScanActivity extends StyledActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler();
    private Camera camera;
    private SurfaceView preview;
    private boolean surfaceReady;
    private boolean scanning;

    private TextView title;
    private TextView hint;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        // Scrims around a clear square frame the code, built from Views only.
        LinearLayout overlay = column();
        LinearLayout top = column();
        top.setBackgroundColor(Theme.SCRIM);
        top.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(28), 0, dp(28), dp(24));
        top.addView(eyebrow(str(R.string.connect_title), Theme.MUTED));
        title = text(str(R.string.qr_title), 22, Theme.TEXT, Theme.extrabold);
        title.setGravity(Gravity.CENTER);
        top.addView(title, margins(wrap(), 0, 6, 0, 0));
        overlay.addView(top, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout middle = row();
        View left = new View(this);
        left.setBackgroundColor(Theme.SCRIM);
        middle.addView(left, new LinearLayout.LayoutParams(0, -1, 1));
        View frame = new View(this);
        android.graphics.drawable.GradientDrawable finder = shape(0x00000000, 18);
        finder.setStroke(dp(3), Theme.ACCENT);
        frame.setBackgroundDrawable(finder);
        middle.addView(frame, size(240, 240));
        View right = new View(this);
        right.setBackgroundColor(Theme.SCRIM);
        middle.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        overlay.addView(middle, new LinearLayout.LayoutParams(-1, dp(240)));
        LinearLayout bottom = column();
        bottom.setBackgroundColor(Theme.SCRIM);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(dp(28), dp(24), dp(28), dp(28));
        hint = text(str(R.string.qr_hint), 14, Theme.SOFT, Theme.regular);
        hint.setGravity(Gravity.CENTER);
        bottom.addView(hint);
        bottom.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        bottom.addView(pill(str(R.string.cancel), R.drawable.ic_close, Widgets.PillButton.SECONDARY, new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        overlay.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        scanning = true;
        startCamera();
    }

    @Override protected void onPause() {
        scanning = false;
        stopCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; startCamera(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; stopCamera(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    private void startCamera() {
        if (!surfaceReady || !scanning || camera != null) return;
        try {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size best = null;
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            for (Camera.Size candidate : sizes) {
                if (candidate.width <= 1280 && candidate.height <= 720 &&
                        (best == null || Math.abs(candidate.width - 800) < Math.abs(best.width - 800))) best = candidate;
            }
            if (best != null) parameters.setPreviewSize(best.width, best.height);
            List<String> focus = parameters.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
            camera.setOneShotPreviewCallback(this);
        } catch (Exception error) {
            stopCamera();
            title.setText(R.string.qr_camera_failed);
            hint.setText(R.string.qr_camera_hint);
        }
    }

    private void stopCamera() {
        if (camera == null) return;
        try { camera.setOneShotPreviewCallback(null); camera.stopPreview(); } catch (Exception ignored) { }
        camera.release();
        camera = null;
    }

    @Override public void onPreviewFrame(final byte[] data, Camera source) {
        if (!scanning) return;
        Camera.Size size = source.getParameters().getPreviewSize();
        final int width = size.width;
        final int height = size.height;
        decoder.execute(new Runnable() {
            @Override public void run() {
                String found = decode(data, width, height);
                if (found != null) {
                    try { InstanceProfile.manifestUrl(QrScanActivity.this, found); }
                    catch (IllegalArgumentException ignored) { found = null; }
                }
                final String result = found;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (!scanning) return;
                        if (result != null) {
                            scanning = false;
                            Intent answer = new Intent();
                            answer.putExtra("manifest_url", result);
                            setResult(RESULT_OK, answer);
                            finish();
                        } else if (camera != null) camera.setOneShotPreviewCallback(QrScanActivity.this);
                    }
                });
            }
        });
    }

    private String decode(byte[] frame, int width, int height) {
        try {
            PlanarYUVLuminanceSource image = new PlanarYUVLuminanceSource(frame, width, height,
                    0, 0, width, height, false);
            Result result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(image)));
            return result.getText();
        } catch (Exception ignored) { }
        try {
            byte[] rotated = new byte[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) rotated[x * height + (height - y - 1)] = frame[y * width + x];
            }
            PlanarYUVLuminanceSource image = new PlanarYUVLuminanceSource(rotated, height, width,
                    0, 0, height, width, false);
            Result result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(image)));
            return result.getText();
        } catch (Exception ignored) { return null; }
    }
}
