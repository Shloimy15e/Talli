package dev.dynamiq.talli.ui;

import dev.dynamiq.talli.model.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

import static dev.dynamiq.talli.ui.Colors.*;

public class ClientDialog extends JDialog {

    private final JTextField nameField;
    private final JTextField emailField;
    private final JTextField rateField;
    private final JComboBox<String> rateTypeField;
    private final JTextArea notesField;
    private JLabel errorLabel;
    private boolean confirmed = false;

    public ClientDialog(Frame owner, Client existing) {
        super(owner, existing != null ? "Edit Client" : "New Client", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        // Overlay
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

        if (owner != null) setBounds(owner.getBounds());
        else setSize(Toolkit.getDefaultToolkit().getScreenSize());

        // Card
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 6; i > 0; i--) {
                    g2.setColor(Theme.shadow());
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2, getHeight() - i * 2, 16, 16);
                }
                g2.setColor(Theme.dialogBg());
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setColor(Theme.border());
                g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setPreferredSize(new Dimension(430, 500));

        // Header
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.dialogHeader());
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
                g2.fillRect(0, getHeight() - 16, getWidth(), 16);
                g2.setColor(Theme.borderSubtle());
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 22, 14, 22));

        JLabel titleLabel = new JLabel(existing != null ? "Edit Client" : "New Client");
        titleLabel.setFont(HEADING);
        titleLabel.setForeground(Theme.text());
        header.add(titleLabel, BorderLayout.WEST);

        JLabel closeBtn = new JLabel(Icons.close(Theme.textMuted()));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { ClientDialog.this.animateClose(); }
            public void mouseEntered(java.awt.event.MouseEvent e) { closeBtn.setIcon(Icons.close(Theme.text())); }
            public void mouseExited(java.awt.event.MouseEvent e) { closeBtn.setIcon(Icons.close(Theme.textMuted())); }
        });
        header.add(closeBtn, BorderLayout.EAST);

        // Draggable
        final Point[] dp = {null};
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) { dp[0] = e.getPoint(); }
            public void mouseReleased(java.awt.event.MouseEvent e) { dp[0] = null; }
        });
        header.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (dp[0] != null) { Point l = getLocation(); setLocation(l.x + e.getX() - dp[0].x, l.y + e.getY() - dp[0].y); }
            }
        });
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(20, 22, 12, 22));

        nameField = styledInput("Client or company name");
        emailField = styledInput("email@example.com");
        rateField = styledInput("0.00");
        rateTypeField = new JComboBox<>(new String[]{"hourly", "project", "retainer"});
        rateTypeField.setBackground(Theme.bgInput());
        rateTypeField.setForeground(Theme.text());
        rateTypeField.setFont(BODY);
        rateTypeField.setPreferredSize(new Dimension(0, 36));
        notesField = styledTextArea();

        if (existing != null) {
            nameField.setText(existing.getName());
            emailField.setText(existing.getEmail());
            rateField.setText(existing.getRate().toPlainString());
            rateTypeField.setSelectedItem(existing.getRateType());
            notesField.setText(existing.getNotes());
        }

        body.add(formRow("NAME", nameField));
        body.add(Box.createVerticalStrut(14));
        body.add(formRow("EMAIL", emailField));
        body.add(Box.createVerticalStrut(14));
        body.add(formRow("RATE ($)", rateField));
        body.add(Box.createVerticalStrut(14));
        body.add(formRow("TYPE", rateTypeField));
        body.add(Box.createVerticalStrut(14));
        JScrollPane ns = new JScrollPane(notesField);
        ns.setBorder(BorderFactory.createEmptyBorder());
        ns.setOpaque(false);
        ns.getViewport().setOpaque(false);
        body.add(formRow("NOTES", ns));

        root.add(body, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(6, 22, 20, 22));

        JButton cancelBtn = styledButton("Cancel", Theme.btnCancel(), Theme.btnCancelHover(), Theme.btnCancelText());
        cancelBtn.addActionListener(e -> animateClose());

        JButton saveBtn = styledButton(
            existing != null ? "Save Changes" : "Add Client",
            Theme.accent(), Theme.accentHover(), Theme.accentText()
        );
        saveBtn.addActionListener(e -> trySubmit());

        footer.add(cancelBtn);
        footer.add(saveBtn);

        errorLabel = new JLabel();
        errorLabel.setFont(SMALL);
        errorLabel.setForeground(Theme.danger());
        errorLabel.setHorizontalAlignment(SwingConstants.LEFT);
        errorLabel.setBorder(new EmptyBorder(0, 22, 8, 22));
        errorLabel.setVisible(false);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        south.add(errorLabel);
        south.add(footer);
        root.add(south, BorderLayout.SOUTH);

        overlay.add(root);
        setContentPane(overlay);

        getRootPane().registerKeyboardAction(e -> animateClose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent e) {
                Anim.fadeIn(ClientDialog.this, 150);
                nameField.requestFocusInWindow();
            }
        });
    }

    private void trySubmit() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { showError("Name is required"); nameField.requestFocusInWindow(); return; }
        String rt = rateField.getText().trim();
        if (rt.isEmpty()) { showError("Rate is required"); rateField.requestFocusInWindow(); return; }
        try { new java.math.BigDecimal(rt); } catch (NumberFormatException e) {
            showError("Rate must be a valid number"); rateField.requestFocusInWindow(); return;
        }
        confirmed = true;
        animateClose();
    }

    private void animateClose() { Anim.fadeOut(this, 120, this::dispose); }

    private void showError(String msg) {
        errorLabel.setIcon(Icons.alert(Theme.danger()));
        errorLabel.setText(msg);
        errorLabel.setIconTextGap(6);
        errorLabel.setVisible(true);
        Timer t = new Timer(4000, e -> errorLabel.setVisible(false));
        t.setRepeats(false);
        t.start();
    }

    public boolean isConfirmed() { return confirmed; }
    public String getClientName() { return nameField.getText().trim(); }
    public String getEmail() { return emailField.getText().trim(); }
    public String getRateText() { return rateField.getText().trim(); }
    public String getRateType() { return (String) rateTypeField.getSelectedItem(); }
    public String getNotes() { return notesField.getText().trim(); }

    private JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(0, 5));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        JLabel lbl = new JLabel(label);
        lbl.setFont(LABEL);
        lbl.setForeground(Theme.textMuted());
        row.add(lbl, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JTextField styledInput(String placeholder) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hasFocus() ? Theme.bgInputFocus() : Theme.bgInput());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(hasFocus() ? Theme.accent() : Theme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        f.setOpaque(false);
        f.setForeground(Theme.text());
        f.setCaretColor(Theme.accent());
        f.setFont(BODY);
        f.setBorder(new EmptyBorder(9, 12, 9, 12));
        f.setPreferredSize(new Dimension(0, 36));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        return f;
    }

    private JTextArea styledTextArea() {
        JTextArea a = new JTextArea(3, 20) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hasFocus() ? Theme.bgInputFocus() : Theme.bgInput());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(hasFocus() ? Theme.accent() : Theme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        a.setOpaque(false);
        a.setForeground(Theme.text());
        a.setCaretColor(Theme.accent());
        a.setFont(BODY);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(9, 12, 9, 12));
        return a;
    }

    private JButton styledButton(String text, Color bg, Color hoverBg, Color fg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
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
        btn.setBorder(new EmptyBorder(9, 18, 9, 18));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hoverBg); btn.repaint(); }
            public void mouseExited(java.awt.event.MouseEvent e) { btn.setBackground(bg); btn.repaint(); }
        });
        return btn;
    }
}
