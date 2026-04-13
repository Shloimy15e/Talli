package com.talli.ui;

import java.awt.Color;
import java.util.prefs.Preferences;

// Manages light/dark mode state. Persists the preference so it survives restarts.
// Like localStorage in web dev, Java's Preferences API stores key-value pairs per user.
public class Theme {
    private static boolean dark = false;
    private static final Preferences prefs = Preferences.userNodeForPackage(Theme.class);

    public static void load() {
        dark = prefs.getBoolean("darkMode", false); // Default: light
    }

    public static void toggle() {
        dark = !dark;
        prefs.putBoolean("darkMode", dark);
    }

    public static boolean isDark() { return dark; }

    // === Dynamic colors that change with theme ===

    // Sidebar stays dark always
    public static Color sidebarBg() { return new Color(18, 24, 38); }
    public static Color sidebarHover() { return new Color(28, 36, 52); }
    public static Color sidebarActive() { return new Color(34, 44, 60); }
    public static Color sidebarText() { return new Color(200, 205, 215); }
    public static Color sidebarTextDim() { return new Color(90, 100, 120); }

    // Content area flips
    public static Color bg() { return dark ? new Color(22, 31, 48) : new Color(247, 247, 250); }
    public static Color bgCard() { return dark ? new Color(28, 38, 58) : Color.WHITE; }
    public static Color bgInput() { return dark ? new Color(34, 44, 66) : new Color(242, 242, 245); }
    public static Color bgInputFocus() { return dark ? new Color(38, 50, 72) : Color.WHITE; }
    public static Color bgHover() { return dark ? new Color(32, 42, 62) : new Color(245, 245, 248); }

    public static Color text() { return dark ? new Color(235, 232, 225) : new Color(22, 31, 48); }
    public static Color textSecondary() { return dark ? new Color(160, 165, 178) : new Color(100, 110, 130); }
    public static Color textMuted() { return dark ? new Color(90, 100, 120) : new Color(155, 162, 175); }

    public static Color border() { return dark ? new Color(42, 52, 72) : new Color(228, 228, 235); }
    public static Color borderSubtle() { return dark ? new Color(34, 44, 64) : new Color(238, 238, 242); }

    public static Color rowHover() { return dark ? new Color(26, 36, 54) : new Color(245, 246, 250); }
    public static Color rowSelected() { return dark ? new Color(36, 48, 68) : new Color(234, 124, 40, 15); }

    public static Color dialogBg() { return dark ? new Color(28, 38, 58) : Color.WHITE; }
    public static Color dialogHeader() { return dark ? new Color(22, 31, 48) : new Color(250, 250, 252); }
    public static Color overlay() { return dark ? new Color(0, 0, 0, 120) : new Color(22, 31, 48, 60); }

    public static Color btnCancel() { return dark ? new Color(42, 52, 72) : new Color(240, 240, 243); }
    public static Color btnCancelHover() { return dark ? new Color(52, 62, 82) : new Color(232, 232, 236); }
    public static Color btnCancelText() { return dark ? new Color(160, 165, 178) : new Color(100, 110, 130); }

    public static Color shadow() { return dark ? new Color(0, 0, 0, 40) : new Color(0, 0, 0, 15); }

    // Accent stays the same in both modes
    public static Color accent() { return new Color(234, 124, 40); }
    public static Color accentHover() { return dark ? new Color(245, 140, 60) : new Color(220, 112, 30); }
    public static Color accentText() { return dark ? new Color(20, 16, 10) : Color.WHITE; }
    public static Color accentSubtle() { return new Color(234, 124, 40, dark ? 40 : 20); }

    public static Color danger() { return new Color(210, 60, 50); }
    public static Color dangerHover() { return dark ? new Color(225, 75, 65) : new Color(190, 50, 42); }

    public static Color toastBg() { return dark ? new Color(240, 240, 245) : new Color(22, 31, 48); }
    public static Color toastText() { return dark ? new Color(22, 31, 48) : new Color(240, 240, 245); }

    // Scrollbar
    public static Color scrollTrack() { return dark ? new Color(22, 31, 48) : new Color(247, 247, 250); }
    public static Color scrollThumb() { return dark ? new Color(55, 65, 85) : new Color(200, 200, 210); }
}
