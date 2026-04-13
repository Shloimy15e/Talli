package com.dynabill;

import com.dynabill.db.Database;
import com.dynabill.ui.MainFrame;
import com.dynabill.ui.Theme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

public class App {

    private static MainFrame frame;

    public static void main(String[] args) {
        Theme.load();
        registerFonts();
        Database.initialize();
        launch();
    }

    // Register bundled Inter font so it's available everywhere.
    // Like @font-face in CSS — load a custom font from a file.
    private static void registerFonts() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            String[] weights = {"Regular", "Medium", "SemiBold", "Bold"};
            for (String w : weights) {
                java.io.InputStream is = App.class.getClassLoader().getResourceAsStream("fonts/Inter-" + w + ".ttf");
                if (is != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                    ge.registerFont(font);
                    is.close();
                }
            }
        } catch (Exception e) {
            // Fall back to system fonts
        }
    }

    // Called on startup and when theme toggles
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            applyTheme();

            if (frame != null) {
                frame.dispose();
            }
            frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void applyTheme() {
        UIManager.put("defaultFont", new Font("Inter", Font.PLAIN, 13));

        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);

        UIManager.put("ScrollBar.width", 7);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track", Theme.scrollTrack());
        UIManager.put("ScrollBar.thumb", Theme.scrollThumb());

        UIManager.put("Panel.background", Theme.bg());
        UIManager.put("Table.background", Theme.bgCard());
        UIManager.put("Table.selectionBackground", Theme.rowSelected());
        UIManager.put("Table.selectionForeground", Theme.text());
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("TableHeader.background", Theme.bgCard());
        UIManager.put("TableHeader.separatorColor", Theme.border());
        UIManager.put("TextField.background", Theme.bgInput());
        UIManager.put("TextArea.background", Theme.bgInput());
        UIManager.put("ComboBox.background", Theme.bgInput());
        UIManager.put("Button.background", Theme.btnCancel());
        UIManager.put("Component.borderColor", Theme.border());
        UIManager.put("Component.focusColor", new Color(234, 124, 40, 80));

        UIManager.put("PopupMenu.background", Theme.bgCard());
        UIManager.put("PopupMenu.borderColor", Theme.border());
        UIManager.put("MenuItem.selectionBackground", Theme.accentSubtle());
        UIManager.put("MenuItem.background", Theme.bgCard());

        if (Theme.isDark()) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }
}
