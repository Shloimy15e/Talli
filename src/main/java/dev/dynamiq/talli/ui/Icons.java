package dev.dynamiq.talli.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.swing.Icon;
import java.awt.Color;

public final class Icons {
    private Icons() {}

    public static Icon get(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon;
    }

    // Default size bumped to 20px to compensate for Lucide's ~30% internal padding
    public static Icon get(String name, Color color) {
        return get(name, 20, color);
    }

    // Nav icons — 20px so the visible stroke is ~14px
    public static Icon clients(Color color) { return get("users", 20, color); }
    public static Icon invoices(Color color) { return get("file-text", 20, color); }
    public static Icon payments(Color color) { return get("credit-card", 20, color); }
    public static Icon dashboard(Color color) { return get("layout-dashboard", 20, color); }

    // Button/action icons — 18px
    public static Icon plus(Color color) { return get("plus", 18, color); }
    public static Icon close(Color color) { return get("x", 18, color); }
    public static Icon edit(Color color) { return get("pencil", 18, color); }
    public static Icon trash(Color color) { return get("trash-2", 18, color); }
    public static Icon alert(Color color) { return get("alert-circle", 18, color); }
}
