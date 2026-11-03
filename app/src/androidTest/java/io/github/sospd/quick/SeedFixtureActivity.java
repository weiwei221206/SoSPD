package io.github.sospd.quick;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.graphics.*;
import android.view.*;

/** Test APK only; deliberately uses no target-app classes so it also runs as a separate process. */
public class SeedFixtureActivity extends Activity {
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); getWindow().getDecorView().invalidate();
        recreate();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        setContentView(new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xFF14212B);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE); paint.setTextSize(getWidth() / 16f); paint.setTypeface(Typeface.MONOSPACE);
                String seed = getIntent().getStringExtra("seed");
                canvas.drawText(seed == null ? "ABC-DEF-GHI" : seed, getWidth() * .1f, getHeight() * .3f, paint);
                paint.setColor(Color.GRAY);
                canvas.drawText("ZZZ-ZZZ-ZZZ", getWidth() * .1f, getHeight() * .75f, paint);
            }
        });
        getWindow().getDecorView().post(() -> {
            if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
                getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        });
    }
}
