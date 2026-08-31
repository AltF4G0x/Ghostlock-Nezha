package com.ghostlock.bootstrap;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;

public class MainActivity extends Activity {
    private TextView tv;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            tv.setText(readLog());
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ll.setPadding(pad, pad, pad, pad);

        Button btn = new Button(this);
        btn.setText("Run exploit now");
        btn.setOnClickListener(v -> Runner.run(getApplicationContext()));
        ll.addView(btn);

        tv = new TextView(this);
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        ll.addView(sv);
        setContentView(ll);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    private String readLog() {
        File log = new File(getFilesDir(), "boot.log");
        if (!log.exists()) return "No run log yet.\nReboot or tap the button.";
        try {
            FileInputStream in = new FileInputStream(log);
            byte[] buf = new byte[(int) Math.min(log.length(), 65536)];
            int n = in.read(buf);
            in.close();
            return n > 0 ? new String(buf, 0, n) : "(log is empty)";
        } catch (Throwable t) {
            return "log read failed: " + t;
        }
    }
}
