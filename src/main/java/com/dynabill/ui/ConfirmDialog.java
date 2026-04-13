package com.dynabill.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

import static com.dynabill.ui.Colors.*;

public class ConfirmDialog extends JDialog {

    private boolean confirmed = false;

    public ConfirmDialog(Frame owner, String title, String message, String confirmText, Color confirmColor) {
        super(owner, title, true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        JPanel overlay = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.overlay());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        overlay.setOpaque(false);
        overlay.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getSource() == overlay) animateClose();
            }
        });

        if (owner != null) {
            setBounds(owner.getBounds());
        } else {
            setSize(Toolkit.getDefaultToolkit().getScreenSize());
        }

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 6; i > 0; i--) {
                    g2.setColor(new Color(0, 0, 0, 5));
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2, 14, 14);
                }
                g2.setColor(Theme.dialogBg());
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
                g2.setColor(Theme.border());
                g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setPreferredSize(new Dimension(380, 175));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 22, 8, 22));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Colors.HEADING);
        titleLabel.setForeground(Theme.text());
        header.add(titleLabel, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4, 22, 16, 22));
        JLabel msg = new JLabel("<html><body style='width:300px'>" + message + "</body></html>");
        msg.setFont(Colors.BODY);
        msg.setForeground(Theme.textSecondary());
        body.add(msg, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 22, 18, 22));

        JButton cancelBtn = styledButton("Cancel", Theme.btnCancel(), Theme.btnCancelHover(), Theme.btnCancelText());
        cancelBtn.addActionListener(e -> animateClose());

        Color hoverColor = new Color(
            Math.max(confirmColor.getRed() - 15, 0),
            Math.max(confirmColor.getGreen() - 15, 0),
            Math.max(confirmColor.getBlue() - 15, 0)
        );
        JButton confirmBtn = styledButton(confirmText, confirmColor, hoverColor, Color.WHITE);
        confirmBtn.addActionListener(e -> { confirmed = true; animateClose(); });

        footer.add(cancelBtn);
        footer.add(confirmBtn);
        root.add(footer, BorderLayout.SOUTH);

        overlay.add(root);
        setContentPane(overlay);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent e) {
                Anim.fadeIn(ConfirmDialog.this, 150);
            }
        });

        getRootPane().registerKeyboardAction(
            e -> animateClose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void animateClose() {
        Anim.fadeOut(this, 120, this::dispose);
    }

    public boolean isConfirmed() { return confirmed; }

    private JButton styledButton(String text, Color bg, Color hoverBg, Color fg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Inter SemiBold", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hoverBg); btn.repaint(); }
            public void mouseExited(java.awt.event.MouseEvent e) { btn.setBackground(bg); btn.repaint(); }
        });
        return btn;
    }
}
