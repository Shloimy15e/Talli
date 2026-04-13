package com.dynabill.ui;

import java.awt.Color;
import java.awt.Font;

public final class Colors {
    private Colors() {}

    // Sidebar — always dark
    public static final Color SIDEBAR_BG = new Color(18, 24, 38);
    public static final Color SIDEBAR_TEXT = new Color(200, 205, 215);
    public static final Color SIDEBAR_TEXT_DIM = new Color(90, 100, 120);

    // Accent — constant
    public static final Color ACCENT = new Color(234, 124, 40);

    // Fonts — Inter (bundled)
    public static final Font TITLE = new Font("Inter", Font.BOLD, 20);
    public static final Font HEADING = new Font("Inter Medium", Font.PLAIN, 14);
    public static final Font BODY = new Font("Inter", Font.PLAIN, 13);
    public static final Font SMALL = new Font("Inter", Font.PLAIN, 11);
    public static final Font LABEL = new Font("Inter SemiBold", Font.PLAIN, 10);
    public static final Font MONO = new Font("Cascadia Code", Font.PLAIN, 12);
}
