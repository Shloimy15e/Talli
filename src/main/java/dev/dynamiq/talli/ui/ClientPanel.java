package dev.dynamiq.talli.ui;

import dev.dynamiq.talli.model.Client;
import dev.dynamiq.talli.service.ClientService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.List;

import static dev.dynamiq.talli.ui.Colors.*;

public class ClientPanel extends JPanel {

    private final ClientService clientService;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel countLabel;
    private final JPanel emptyState;
    private final JPanel tableCard;
    private int hoveredRow = -1;

    public ClientPanel(ClientService clientService) {
        this.clientService = clientService;
        setLayout(new BorderLayout());
        setOpaque(false); // Let parent's rounded corner paint show through

        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(24, 32, 16, 32));

        JPanel titleArea = new JPanel();
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleArea.setOpaque(false);

        JLabel title = new JLabel("Clients");
        title.setFont(TITLE);
        title.setForeground(Theme.text());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleArea.add(title);
        titleArea.add(Box.createVerticalStrut(3));

        countLabel = new JLabel("0 clients");
        countLabel.setFont(SMALL);
        countLabel.setForeground(Theme.textMuted());
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleArea.add(countLabel);

        header.add(titleArea, BorderLayout.WEST);

        JButton addBtn = createAccentButton("New Client");
        addBtn.setIcon(Icons.plus(Theme.accentText()));
        addBtn.setIconTextGap(6);
        addBtn.addActionListener(e -> showDialog(null));
        header.add(addBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // --- Empty state ---
        emptyState = new JPanel();
        emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
        emptyState.setOpaque(false);
        emptyState.setBorder(new EmptyBorder(60, 32, 32, 32));

        JLabel emptyIcon = new JLabel(Icons.get("users", 40, Theme.border()));
        emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(emptyIcon);
        emptyState.add(Box.createVerticalStrut(14));

        JLabel emptyTitle = new JLabel("No clients yet");
        emptyTitle.setFont(HEADING);
        emptyTitle.setForeground(Theme.textSecondary());
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(emptyTitle);
        emptyState.add(Box.createVerticalStrut(6));

        JLabel emptyHint = new JLabel("Add your first client to start tracking billing.");
        emptyHint.setFont(BODY);
        emptyHint.setForeground(Theme.textMuted());
        emptyHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(emptyHint);
        emptyState.add(Box.createVerticalStrut(20));

        JButton emptyBtn = createAccentButton("Add Client");
        emptyBtn.setIcon(Icons.plus(Theme.accentText()));
        emptyBtn.setIconTextGap(6);
        emptyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyBtn.addActionListener(e -> showDialog(null));
        emptyState.add(emptyBtn);

        // --- Table ---
        String[] columns = {"", "Name", "Email", "Rate", "Type"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setBackground(Theme.bgCard());
        table.setForeground(Theme.text());
        table.setSelectionBackground(Theme.rowSelected());
        table.setSelectionForeground(Theme.text());
        table.setRowHeight(48);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFocusable(false);
        table.getTableHeader().setReorderingAllowed(false);

        // Hide ID column
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setPreferredWidth(0);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);

        // Header renderer
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = new JLabel(value != null ? value.toString().toUpperCase() : "");
                lbl.setFont(LABEL);
                lbl.setForeground(Theme.textMuted());
                lbl.setBackground(Theme.bgCard());
                lbl.setOpaque(true);
                lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.borderSubtle()),
                    new EmptyBorder(12, 24, 12, 24)
                ));
                return lbl;
            }
        });

        // Cell renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setBorder(new EmptyBorder(0, 24, 0, 24));
                setFont(BODY);

                if (isSelected) {
                    setBackground(Theme.rowSelected());
                } else if (row == hoveredRow) {
                    setBackground(Theme.rowHover());
                } else {
                    setBackground(Theme.bgCard());
                }

                if (col == 1) {
                    setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
                    setForeground(Theme.text());
                } else if (col == 3) {
                    setFont(MONO);
                    setForeground(Theme.accent());
                } else {
                    setForeground(Theme.textSecondary());
                }
                return this;
            }
        });

        // Hover
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != hoveredRow) { hoveredRow = row; table.repaint(); }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) { hoveredRow = -1; table.repaint(); }
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editSelected(); }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Theme.bgCard());
        scrollPane.getViewport().setBackground(Theme.bgCard());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Apple-style card with soft shadow — clipped rounded corners
        tableCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Shadow
                for (int i = 5; i > 0; i--) {
                    g2.setColor(Theme.shadow());
                    g2.fillRoundRect(i, i + 1, getWidth() - i * 2, getHeight() - i * 2, 12, 12);
                }
                // Card
                g2.setColor(Theme.bgCard());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }

            @Override
            protected void paintChildren(Graphics g) {
                // Clip children to rounded rect so table corners aren't boxy
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        tableCard.setOpaque(false);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        // Right-click menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit", Icons.edit(Theme.textSecondary()));
        JMenuItem deleteItem = new JMenuItem("Delete", Icons.trash(Theme.danger()));
        editItem.addActionListener(e -> editSelected());
        deleteItem.addActionListener(e -> deleteSelected());
        deleteItem.setForeground(Theme.danger());
        popup.add(editItem);
        popup.addSeparator();
        popup.add(deleteItem);
        table.setComponentPopupMenu(popup);

        refreshTable();
    }

    private void showDialog(Client existing) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ClientDialog dialog = new ClientDialog(owner, existing);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            BigDecimal rate = new BigDecimal(dialog.getRateText());
            if (existing != null) {
                existing.setName(dialog.getClientName());
                existing.setEmail(dialog.getEmail());
                existing.setRate(rate);
                existing.setRateType(dialog.getRateType());
                existing.setNotes(dialog.getNotes());
                clientService.update(existing);
            } else {
                clientService.create(new Client(
                    dialog.getClientName(), dialog.getEmail(), rate,
                    dialog.getRateType(), dialog.getNotes()
                ));
            }
            refreshTable();
            showToast(existing != null ? "Client updated" : "Client added");
        }
    }

    private void showToast(String message) {
        JPanel toast = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fillRoundRect(2, 3, getWidth() - 4, getHeight() - 4, 10, 10);
                g2.setColor(Theme.toastBg());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(new EmptyBorder(10, 16, 10, 16));
        toast.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));

        JLabel text = new JLabel(message);
        text.setForeground(Theme.toastText());
        text.setFont(BODY);
        toast.add(text);

        toast.setSize(toast.getPreferredSize());
        JLayeredPane layered = getRootPane().getLayeredPane();
        Point loc = SwingUtilities.convertPoint(this, (getWidth() - toast.getPreferredSize().width) / 2, 8, layered);
        toast.setBounds(loc.x, loc.y, toast.getPreferredSize().width, toast.getPreferredSize().height);
        layered.add(toast, JLayeredPane.POPUP_LAYER);
        layered.repaint();

        Timer timer = new Timer(2000, e -> { layered.remove(toast); layered.repaint(); });
        timer.setRepeats(false);
        timer.start();
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int id = (int) tableModel.getValueAt(row, 0);
        Client client = clientService.getById(id);
        if (client != null) showDialog(client);
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int id = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ConfirmDialog confirm = new ConfirmDialog(owner,
            "Delete Client",
            "Delete \"" + name + "\"? This cannot be undone.",
            "Delete", Theme.danger()
        );
        confirm.setVisible(true);
        if (confirm.isConfirmed()) {
            clientService.delete(id);
            refreshTable();
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        List<Client> clients = clientService.getAll();
        for (Client c : clients) {
            tableModel.addRow(new Object[]{
                c.getId(), c.getName(), c.getEmail(),
                "$" + c.getRate(), c.getRateType()
            });
        }

        int count = clients.size();
        countLabel.setText(count + " client" + (count != 1 ? "s" : ""));

        Component center = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (center != null) remove(center);

        if (count == 0) {
            add(emptyState, BorderLayout.CENTER);
        } else {
            JPanel cardWrapper = new JPanel(new BorderLayout());
            cardWrapper.setOpaque(false);
            cardWrapper.setBorder(new EmptyBorder(0, 32, 24, 32));
            cardWrapper.add(tableCard, BorderLayout.CENTER);
            add(cardWrapper, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    private JButton createAccentButton(String text) {
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
        btn.setBackground(Theme.accent());
        btn.setForeground(Theme.accentText());
        btn.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 16, 9, 16));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(Theme.accentHover()); btn.repaint(); }
            public void mouseExited(MouseEvent e) { btn.setBackground(Theme.accent()); btn.repaint(); }
        });
        return btn;
    }
}
