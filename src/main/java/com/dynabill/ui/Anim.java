package com.dynabill.ui;

import javax.swing.*;
import java.awt.*;

// Simple animation utility — interpolates a float from 0 to 1 over a duration.
// Like CSS `transition: all 200ms ease-out` but manual.
public final class Anim {
    private Anim() {}

    public interface Update {
        void apply(float progress); // 0.0 -> 1.0
    }

    // Run an animation over `durationMs` milliseconds, calling `update` each frame.
    // Uses ease-out curve (fast start, slow end) — same as CSS ease-out.
    public static void run(int durationMs, Update update) {
        int fps = 60;
        int delay = 1000 / fps;
        long start = System.currentTimeMillis();

        Timer timer = new Timer(delay, null);
        timer.addActionListener(e -> {
            float elapsed = System.currentTimeMillis() - start;
            float raw = Math.min(elapsed / durationMs, 1.0f);
            // Ease-out cubic — decelerates naturally
            float eased = 1.0f - (1.0f - raw) * (1.0f - raw) * (1.0f - raw);
            update.apply(eased);
            if (raw >= 1.0f) {
                timer.stop();
            }
        });
        timer.start();
    }

    // Fade in a window from 0 to 1 opacity
    public static void fadeIn(Window window, int durationMs) {
        window.setOpacity(0f);
        run(durationMs, progress -> {
            if (window.isDisplayable()) {
                window.setOpacity(Math.min(progress, 1f));
            }
        });
    }

    // Fade out then dispose
    public static void fadeOut(Window window, int durationMs, Runnable onComplete) {
        run(durationMs, progress -> {
            if (window.isDisplayable()) {
                window.setOpacity(Math.max(1f - progress, 0f));
                if (progress >= 1f && onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    // Smooth color transition for hover effects
    public static Color lerp(Color from, Color to, float t) {
        int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * t);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        int a = (int) (from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t);
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)),
            Math.max(0, Math.min(255, a))
        );
    }
}
