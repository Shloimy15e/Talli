package com.talli.ui;

import com.talli.App;
import com.talli.service.ClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.talli.ui.Colors.*;

public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("Talli");
        setSize(1080, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 500));

        // App icon
        try {
            Image src = new ImageIcon(getClass().getClassLoader().getResource("icon-full.png")).getImage();
            java.util.List<Image> icons = new java.util.ArrayList<>();
            icons.add(src.getScaledInstance(16, 16, Image.SCALE_SMOOTH));
            icons.add(src.getScaledInstance(32, 32, Image.SCALE_SMOOTH));
            icons.add(src.getScaledInstance(48, 48, Image.SCALE_SMOOTH));
            icons.add(src.getScaledInstance(256, 256, Image.SCALE_SMOOTH));
            setIconImages(icons);
        } catch (Exception e) {}

        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.sidebarBg());
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.sidebarText());

        ClientService clientService = new ClientService();

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.sidebarBg()); // Sidebar color fills any gap

        // === SIDEBAR (always dark) ===
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.sidebarBg());
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBorder(new EmptyBorder(20, 12, 16, 12));

        // Logo
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logoPanel.setOpaque(false);
        logoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        logoPanel.setBorder(new EmptyBorder(4, 6, 0, 0));

        try {
            ImageIcon logoImg = new ImageIcon(getClass().getClassLoader().getResource("icon-full.png"));
            Image scaled = logoImg.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH);
            logoPanel.add(new JLabel(new ImageIcon(scaled)));
        } catch (Exception e) {}

        JLabel logoLabel = new JLabel("Talli");
        logoLabel.setFont(new Font("Inter", Font.BOLD, 16));
        logoLabel.setForeground(new Color(240, 240, 245));
        logoPanel.add(logoLabel);

        sidebar.add(logoPanel);
        sidebar.add(Box.createVerticalStrut(2));

        JLabel subtitle = new JLabel("Billing & Finance");
        subtitle.setFont(SMALL);
        subtitle.setForeground(Theme.sidebarTextDim());
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(0, 14, 0, 0));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(32));

        JLabel section = new JLabel("WORKSPACE");
        section.setFont(LABEL);
        section.setForeground(Theme.sidebarTextDim());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new EmptyBorder(0, 14, 8, 0));
        sidebar.add(section);

        // Nav items
        String[] navNames = {"Clients", "Invoices", "Payments", "Dashboard"};
        String[] navIcons = {"users", "file-text", "credit-card", "layout-dashboard"};

        for (int i = 0; i < navNames.length; i++) {
            boolean active = (i == 0);
            final String iconName = navIcons[i];

            JPanel navBtn = new JPanel(new BorderLayout(8, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
            };
            navBtn.setOpaque(false);
            navBtn.setBorder(new EmptyBorder(8, 10, 8, 10));
            navBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            navBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            navBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color iconColor = active ? Theme.accent() : Theme.sidebarTextDim();
            navBtn.add(new JLabel(Icons.get(iconName, 20, iconColor)), BorderLayout.WEST);

            JLabel label = new JLabel(navNames[i]);
            label.setFont(BODY);
            label.setForeground(active ? new Color(240, 240, 245) : Theme.sidebarTextDim());
            navBtn.add(label, BorderLayout.CENTER);

            if (active) {
                navBtn.setBackground(Theme.sidebarActive());
            } else {
                navBtn.setBackground(Theme.sidebarBg());
                navBtn.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { navBtn.setBackground(Theme.sidebarHover()); navBtn.repaint(); }
                    public void mouseExited(MouseEvent e) { navBtn.setBackground(Theme.sidebarBg()); navBtn.repaint(); }
                });
            }

            sidebar.add(navBtn);
            sidebar.add(Box.createVerticalStrut(2));
        }

        sidebar.add(Box.createVerticalGlue());

        // --- Theme toggle ---
        JPanel toggleBtn = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        toggleBtn.setOpaque(false);
        toggleBtn.setBackground(Theme.sidebarBg());
        toggleBtn.setBorder(new EmptyBorder(7, 10, 7, 10));
        toggleBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        toggleBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String modeLabel = Theme.isDark() ? "Light Mode" : "Dark Mode";
        JLabel toggleLabel = new JLabel(modeLabel);
        toggleLabel.setFont(SMALL);
        toggleLabel.setForeground(Theme.sidebarTextDim());
        toggleBtn.add(toggleLabel, BorderLayout.CENTER);

        toggleBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                Theme.toggle();
                App.launch();
            }
            public void mouseEntered(MouseEvent e) { toggleBtn.setBackground(Theme.sidebarHover()); toggleBtn.repaint(); }
            public void mouseExited(MouseEvent e) { toggleBtn.setBackground(Theme.sidebarBg()); toggleBtn.repaint(); }
        });

        sidebar.add(toggleBtn);
        sidebar.add(Box.createVerticalStrut(8));

        JLabel version = new JLabel("v1.0.0");
        version.setFont(new Font("Inter", Font.PLAIN, 9));
        version.setForeground(new Color(40, 50, 65));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(new EmptyBorder(0, 14, 0, 0));
        sidebar.add(version);

        root.add(sidebar, BorderLayout.WEST);

        // === CONTENT with rounded left edge (Apple inset effect) ===
        JPanel contentWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.bg());
                // Rounded left corners, straight right
                g2.fillRoundRect(0, 0, getWidth() + 20, getHeight(), 14, 14);
                g2.fillRect(14, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        contentWrapper.setOpaque(false);
        contentWrapper.add(new ClientPanel(clientService), BorderLayout.CENTER);
        root.add(contentWrapper, BorderLayout.CENTER);

        setContentPane(root);
    }
}
