import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EventObject;

public class Main extends JFrame {

    static final Cursor HAND = new Cursor(Cursor.HAND_CURSOR);
    private static final String SFADSMS = System.getProperty("user.home") + File.separator + ".SFADSMS";
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
    static final String dateFormat = NOW.format(FORMATTER);
    static String filePath = System.getProperty("user.dir");
    static final Image img = new ImageIcon(filePath + File.separator + "src" + File.separator + "img" + File.separator + "png.png").getImage();
    static final ImageIcon icon = new ImageIcon(filePath + File.separator + "src" + File.separator + "img" + File.separator + "png.png");
    static Font mainFont;
    static Font plainMainFont;
    static Color mainColor = new Color(117, 119, 255);
    static Color backGround = new Color(225, 225, 225);

    private final int rowsPerPage = 100;
    private double heightMultiplier = 1.0;
    private double widthMultiplier = 1.0;

    private String currentCategory = "";
    private String currentSubFolder = "";
    private String currentSearchQuery = "";
    private String currentSortType = "alpha";
    private boolean currentSortReverse = false;

    private final JScrollPane dataPane;
    private final JPanel sidebarContainer;

    Main() {
        UI.loadCustomFont();
        UI.loadCustomPlainFont();
        this.setIconImage(icon.getImage());
        GraphicsConfiguration gc = this.getGraphicsConfiguration();
        Rectangle bounds = gc.getBounds();
        Insets si = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int yAxis = bounds.width - si.left - si.right;
        int xAxis = bounds.height - si.top - si.bottom;

        widthMultiplier = (double) yAxis / 1920;
        heightMultiplier = (double) xAxis / 1080;

        this.setMinimumSize(new Dimension(yAxis / 2, xAxis / 2));
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel mainWindow = new JPanel(new GridBagLayout());
        mainWindow.setBackground(backGround);
        this.setContentPane(mainWindow);

        JPanel topArea = UI.getJPanel(heightMultiplier);
        UI.addGBComponent(mainWindow, topArea, 1, 0, 0, 1, 1, 0,
                GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER,
                new Dimension(0, (int) (160 * heightMultiplier)));

        JPanel leftSide = new JPanel(new BorderLayout());
        sidebarContainer = new JPanel();
        sidebarContainer.setBackground(mainColor);
        sidebarContainer.setLayout(new BoxLayout(sidebarContainer, BoxLayout.Y_AXIS));

        dataPane = dataArea();
        refreshFolderButtons(sidebarContainer, heightMultiplier, dataPane);

        JScrollPane sideScroll = new JScrollPane(sidebarContainer);
        sideScroll.setBorder(null);
        leftSide.add(sideScroll, BorderLayout.CENTER);

        JPanel topSide = UI.getJPanel(heightMultiplier, widthMultiplier);
        leftSide.add(topSide, BorderLayout.NORTH);
        UI.addGBComponent(mainWindow, leftSide, 0, 0, 1, 3, 0, 1,
                GridBagConstraints.VERTICAL, GridBagConstraints.CENTER,
                new Dimension((int) (280 * widthMultiplier), 0));

        JPanel sortArea = UI.getPanel(heightMultiplier, widthMultiplier);
        UI.addGBComponent(mainWindow, sortArea, 1, 1, 1, 1, 1, 0,
                GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Dimension(0, (int) (140 * heightMultiplier)));

        Dimension btnDim = new Dimension((int) (widthMultiplier * 200), (int) (heightMultiplier * 50));
        int bp = (int) (widthMultiplier * 20);

        JButton date = UI.buttonDesign();
        date.setText("Newest to Oldest");
        date.addActionListener(e -> {
            currentSortType = "date";
            refresh();
        });
        UI.addGBComponent(sortArea, date, 0, 0, 1, 1, 0, 0,
                GridBagConstraints.NONE, GridBagConstraints.WEST, btnDim, 0, bp * 8, 0, bp);

        JButton alphabet = UI.buttonDesign();
        alphabet.setText("A-Z");
        alphabet.addActionListener(e -> {
            currentSortType = "alpha";
            refresh();
        });
        UI.addGBComponent(sortArea, alphabet, 1, 0, 1, 1, 0, 0,
                GridBagConstraints.NONE, GridBagConstraints.WEST, btnDim, 0, bp, 0, bp);

        JButton reverse = UI.buttonDesign();
        boolean[] bool = {false};
        reverse.addActionListener(e -> {
            bool[0] = !bool[0];
            currentSortReverse = bool[0];
            alphabet.setText(bool[0] ? "Z-A" : "A-Z");
            date.setText(bool[0] ? "Oldest to Newest" : "Newest to Oldest");
            refresh();
        });
        reverse.setText("Reverse");
        UI.addGBComponent(sortArea, reverse, 2, 0, 1, 1, 0, 0,
                GridBagConstraints.NONE, GridBagConstraints.WEST, btnDim, 0, bp, 0, bp);

        JButton upload = UI.buttonDesign();
        final String[] defaultDir = {System.getProperty("user.home")};
        upload.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Choose a File", FileDialog.LOAD);
            fd.setLocationRelativeTo(this);
            fd.setDirectory(defaultDir[0]);
            fd.setFilenameFilter((f, n) -> {
                String v = n.toLowerCase();
                return v.endsWith(".png") || v.endsWith(".jpeg") || v.endsWith(".jpg")
                        || v.endsWith(".pdf") || v.endsWith(".doc") || v.endsWith(".docx");
            });
            fd.setVisible(true);
            if (fd.getFile() != null)
                showUploadDialog(fd, sidebarContainer, defaultDir);
        });
        upload.setText("Upload");
        UI.addGBComponent(sortArea, upload, 3, 0, 1, 1, 0, 0,
                GridBagConstraints.NONE, GridBagConstraints.WEST, btnDim, 0, bp, 0, bp);

        JTextField search = UI.getJTextField();
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            public void filter() {
                currentSearchQuery = search.getText().trim();
                refreshFolderButtons(sidebarContainer, heightMultiplier, dataPane);
                refresh();
            }
        });
        UI.addGBComponent(sortArea, search, 4, 0, 1, 1, 1, 0,
                GridBagConstraints.NONE, GridBagConstraints.EAST,
                new Dimension((int) (widthMultiplier * 400), (int) (heightMultiplier * 60)),
                0, bp, 0, bp * 5);

        int pad = (int) (widthMultiplier * 20);
        UI.addGBComponent(mainWindow, dataPane, 1, 2, 1, 1, 1, 1,
                GridBagConstraints.VERTICAL, GridBagConstraints.CENTER,
                new Dimension((int) (1500 * widthMultiplier), (int) (750 * heightMultiplier)),
                0, pad, 0, pad);
    }

    public static void main(String[] args) {
        // Show login BEFORE creating or showing the main window
        JFrame splash = new JFrame();
        splash.setUndecorated(true);
        splash.setVisible(false);

        boolean loggedIn = Auth.showLoginDialog(splash);
        splash.dispose();

        if (!loggedIn) {
            System.exit(0);
        }

        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================
    static JButton actionBtn(String label, Color bg, float fontSize) {
        JButton btn = new JButton(label);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(plainMainFont.deriveFont(Font.BOLD, fontSize));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(HAND);
        Color darker = bg.darker();
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(darker);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }

    private static JTextField buildTextField(String initialText, float fontSize, String placeholder) {
        JTextField tf = new JTextField(initialText) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(160, 160, 160));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    g2.drawString(placeholder, 8, (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 2);
                    g2.dispose();
                }
            }
        };
        tf.setFont(plainMainFont.deriveFont(Font.PLAIN, fontSize));
        tf.setForeground(Color.BLACK);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        SwingUtilities.invokeLater(tf::selectAll);
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(tf::selectAll);
            }
        });
        return tf;
    }

    // ── Navigation helpers ────────────────────────────────────────────────────
    private void refresh() {
        if (currentCategory.isEmpty()) return;
        if (currentSubFolder.isEmpty()) showSubFolders(currentCategory, dataPane, 0);
        else showFiles(currentCategory, currentSubFolder, dataPane, 0);
    }

    // =========================================================================
    // LEVEL 1 — sub-folders inside a category
    // =========================================================================
    public void showSubFolders(String category, JScrollPane scrollPane, int page) {
        currentCategory = category;
        currentSubFolder = "";

        String[] all = Fetcher.getSubFolders(category);
        ArrayList<String> filtered = new ArrayList<>();
        for (String s : all)
            if (currentSearchQuery.isEmpty()
                    || s.toLowerCase().contains(currentSearchQuery.toLowerCase()))
                filtered.add(s);

        Comparator<String> comp = String.CASE_INSENSITIVE_ORDER;
        if (currentSortReverse) comp = comp.reversed();
        filtered.sort(comp);

        int totalRows = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalRows / rowsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * rowsPerPage, end = Math.min(start + rowsPerPage, totalRows);

        String[] cols = {"#", "Name", "Actions"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return c == 2;
            }
        };
        for (int i = start; i < end; i++)
            model.addRow(new Object[]{i + 1, filtered.get(i), filtered.get(i)});

        float tFont = Math.max(15f, (float) (15 * heightMultiplier));
        float hFont = Math.max(16f, (float) (16 * heightMultiplier));
        float pFont = Math.max(14f, (float) (14 * heightMultiplier));

        JTable table = buildTable(tFont, hFont);
        table.setModel(model);
        table.setRowHeight((int) Math.max(50, 50 * heightMultiplier));

        table.getColumnModel().getColumn(0).setPreferredWidth((int) (44 * widthMultiplier));
        table.getColumnModel().getColumn(0).setMaxWidth((int) (60 * widthMultiplier));

        table.getColumnModel().getColumn(1).setCellRenderer((t, v, sel, foc, row, col) -> {
            JLabel lbl = new JLabel(v == null ? "" : v.toString());
            lbl.setFont(plainMainFont.deriveFont(Font.BOLD, tFont));
            lbl.setForeground(mainColor);
            lbl.setCursor(HAND);
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            lbl.setBackground(sel ? new Color(117, 119, 255, 60) : Color.WHITE);
            return lbl;
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() < 2) return; // double-click only
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 1)
                    showFiles(category, (String) table.getValueAt(row, 1), scrollPane, 0);
            }
        });

        // Actions column: Edit + Move + Delete
        // Hard minimum so buttons never disappear on lower-res screens
        int actW = Math.max(230, (int) (280 * widthMultiplier));
        table.getColumnModel().getColumn(2).setMinWidth(actW);
        table.getColumnModel().getColumn(2).setMaxWidth(actW);
        table.getColumn("Actions").setCellRenderer(new SubFolderActionRenderer(tFont));
        table.getColumn("Actions").setCellEditor(
                new SubFolderActionEditor(tFont, category, scrollPane, page));

        JPanel pp = buildPagination(pFont, page, totalPages, totalRows,
                p -> showSubFolders(category, scrollPane, p));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(pp, BorderLayout.SOUTH);

        scrollPane.setViewportView(panel);
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    // =========================================================================
    // LEVEL 2 — files inside a sub-folder
    // =========================================================================
    public void showFiles(String category, String subFolder,
                          JScrollPane scrollPane, int page) {
        currentCategory = category;
        currentSubFolder = subFolder;

        String[] all = Fetcher.getFolderData(category, subFolder);
        ArrayList<String> filtered = new ArrayList<>();
        for (String s : all)
            if (currentSearchQuery.isEmpty()
                    || s.toLowerCase().contains(currentSearchQuery.toLowerCase()))
                filtered.add(s);

        Comparator<String> comp = String.CASE_INSENSITIVE_ORDER;
        if (currentSortReverse) comp = comp.reversed();
        filtered.sort(comp);

        int totalRows = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalRows / rowsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * rowsPerPage, end = Math.min(start + rowsPerPage, totalRows);

        String[] cols = {"#", "File", "Actions"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return c == 2;
            }
        };
        for (int i = start; i < end; i++)
            model.addRow(new Object[]{i + 1, filtered.get(i), filtered.get(i)});

        float tFont = Math.max(15f, (float) (15 * heightMultiplier));
        float hFont = Math.max(16f, (float) (16 * heightMultiplier));
        float pFont = Math.max(14f, (float) (14 * heightMultiplier));

        JTable table = buildTable(tFont, hFont);
        table.setModel(model);
        table.setRowHeight((int) Math.max(50, 50 * heightMultiplier));

        table.getColumnModel().getColumn(0).setPreferredWidth((int) (44 * widthMultiplier));
        table.getColumnModel().getColumn(0).setMaxWidth((int) (60 * widthMultiplier));

        // Actions: View + Print + Move + Delete
        // Hard minimum so buttons never disappear on lower-res screens (wider — includes Rename)
        int actW = Math.max(290, (int) (370 * widthMultiplier));
        table.getColumnModel().getColumn(2).setMinWidth(actW);
        table.getColumnModel().getColumn(2).setMaxWidth(actW);
        table.getColumn("Actions").setCellRenderer(new FileActionRenderer(tFont));
        table.getColumn("Actions").setCellEditor(
                new FileActionEditor(tFont, category, subFolder, scrollPane, page));

        JButton backBtn = UI.buttonDesign();
        backBtn.setText("← Back to " + category);
        backBtn.setFont(plainMainFont.deriveFont(Font.BOLD, pFont));
        backBtn.addActionListener(e -> showSubFolders(category, scrollPane, 0));

        JLabel breadcrumb = new JLabel(category + "  ›  " + subFolder);
        breadcrumb.setFont(plainMainFont.deriveFont(Font.BOLD, pFont));
        breadcrumb.setForeground(new Color(80, 80, 80));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        topBar.setBackground(new Color(245, 245, 250));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        topBar.add(backBtn);
        topBar.add(breadcrumb);

        JPanel pp = buildPagination(pFont, page, totalPages, totalRows,
                p -> showFiles(category, subFolder, scrollPane, p));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.add(topBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(pp, BorderLayout.SOUTH);

        scrollPane.setViewportView(panel);
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    // =========================================================================
    // Table factory
    // =========================================================================
    private JTable buildTable(float tFont, float hFont) {
        JTable t = new JTable();
        t.setFont(plainMainFont.deriveFont(Font.PLAIN, tFont));
        t.setForeground(Color.BLACK);
        t.setBackground(Color.WHITE);
        t.setGridColor(new Color(220, 220, 220));
        t.setSelectionBackground(new Color(117, 119, 255, 60));
        t.setSelectionForeground(Color.BLACK);
        t.getTableHeader().setFont(plainMainFont.deriveFont(Font.BOLD, hFont));
        t.getTableHeader().setForeground(Color.BLACK);
        t.getTableHeader().setBackground(new Color(240, 240, 245));
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    private JPanel buildSubFolderPanel(float fs, boolean interactive,
                                       String name, Runnable onEdit,
                                       Runnable onMove, Runnable onDelete) {
        int btnW = Math.max(65, (int) (72 * widthMultiplier));
        int btnH = Math.max(28, (int) (34 * heightMultiplier));
        Dimension d = new Dimension(btnW, btnH);
        float btnFont = Math.max(10f, (float) (11 * heightMultiplier)); // smaller than cell font so text fits

        JButton editBtn = actionBtn("Edit", new Color(39, 174, 96), btnFont);
        JButton moveBtn = actionBtn("Move", new Color(230, 126, 34), btnFont);
        JButton deleteBtn = actionBtn("Delete", new Color(192, 57, 43), btnFont);
        editBtn.setPreferredSize(d);
        moveBtn.setPreferredSize(d);
        deleteBtn.setPreferredSize(d);

        if (interactive) {
            editBtn.addActionListener(e -> onEdit.run());
            moveBtn.addActionListener(e -> onMove.run());
            deleteBtn.addActionListener(e -> onDelete.run());
        }
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 5));
        p.setBackground(Color.WHITE);
        p.add(editBtn);
        p.add(moveBtn);
        p.add(deleteBtn);
        return p;
    }

    private JPanel buildFilePanel(float fs, boolean interactive,
                                  String name, Runnable onView, Runnable onPrint,
                                  Runnable onRename, Runnable onMove, Runnable onDelete) {
        int btnW = Math.max(54, (int) (60 * widthMultiplier));
        int btnH = Math.max(28, (int) (34 * heightMultiplier));
        Dimension d = new Dimension(btnW, btnH);
        float btnFont = Math.max(10f, (float) (11 * heightMultiplier));

        JButton viewBtn   = actionBtn("View",   new Color(52, 152, 219),  btnFont);
        JButton printBtn  = actionBtn("Print",  new Color(143, 101, 189), btnFont);
        JButton renameBtn = actionBtn("Rename", new Color(39, 174, 96),   btnFont);
        JButton moveBtn   = actionBtn("Move",   new Color(230, 126, 34),  btnFont);
        JButton deleteBtn = actionBtn("Delete", new Color(192, 57, 43),   btnFont);
        viewBtn.setPreferredSize(d);   printBtn.setPreferredSize(d);
        renameBtn.setPreferredSize(d); moveBtn.setPreferredSize(d);
        deleteBtn.setPreferredSize(d);

        if (interactive) {
            viewBtn  .addActionListener(e -> onView  .run());
            printBtn .addActionListener(e -> onPrint .run());
            renameBtn.addActionListener(e -> onRename.run());
            moveBtn  .addActionListener(e -> onMove  .run());
            deleteBtn.addActionListener(e -> onDelete.run());
        }
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 5));
        p.setBackground(Color.WHITE);
        p.add(viewBtn); p.add(printBtn); p.add(renameBtn); p.add(moveBtn); p.add(deleteBtn);
        return p;
    }

    // =========================================================================
    // Rename sub-folder dialog
    // =========================================================================
    private void showRenameSubFolderDialog(String category, String oldName,
                                           JScrollPane scrollPane, int page) {
        JDialog dialog = new JDialog(this, "Rename Folder", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setResizable(false);
        dialog.setSize((int) (widthMultiplier * 520), (int) (heightMultiplier * 280));
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(Color.WHITE);

        float labelSize = Math.max(18f, (float) (18 * heightMultiplier));
        float hintSize = Math.max(13f, (float) (13 * heightMultiplier));
        float inputSize = Math.max(16f, (float) (16 * heightMultiplier));
        float btnSize = Math.max(16f, (float) (16 * heightMultiplier));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 24, 6, 24);

        JLabel titleLbl = new JLabel("Rename Folder:");
        titleLbl.setFont(plainMainFont.deriveFont(Font.BOLD, labelSize));
        titleLbl.setForeground(Color.BLACK);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.add(titleLbl, gbc);

        JLabel hintLbl = new JLabel("Current:  " + oldName);
        hintLbl.setFont(plainMainFont.deriveFont(Font.ITALIC, hintSize));
        hintLbl.setForeground(new Color(100, 100, 100));
        gbc.gridy = 1;
        dialog.add(hintLbl, gbc);

        JTextField tf = buildTextField(oldName, inputSize, "Enter new folder name...");
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        dialog.add(tf, gbc);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setBackground(Color.WHITE);

        JButton saveBtn = actionBtn("Save", new Color(39, 174, 96), btnSize);
        JButton cancelBtn = actionBtn("Cancel", new Color(130, 130, 130), btnSize);

        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                check();
            }

            public void removeUpdate(DocumentEvent e) {
                check();
            }

            public void insertUpdate(DocumentEvent e) {
                check();
            }

            public void check() {
                saveBtn.setEnabled(!tf.getText().trim().isEmpty());
            }
        });

        saveBtn.addActionListener(e -> {
            String newName = tf.getText().trim();
            if (newName.isEmpty() || newName.equals(oldName)) {
                dialog.dispose();
                return;
            }
            renameSubFolder(category, oldName, newName);
            dialog.dispose();
            showSubFolders(category, scrollPane, page);
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnRow.add(saveBtn);
        btnRow.add(cancelBtn);
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 24, 16, 24);
        dialog.add(btnRow, gbc);
        dialog.setVisible(true);
    }

    // =========================================================================
    // Upload dialog
    // =========================================================================
    private void showUploadDialog(FileDialog fd, JPanel container, String[] defaultDir) {
        File file = new File(fd.getDirectory(), fd.getFile());
        String orig = file.getName();
        String noExt = orig.contains(".") ? orig.substring(0, orig.lastIndexOf(".")) : orig;

        JDialog dialog = new JDialog(this, "Upload File", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setResizable(false);
        dialog.setSize((int) (widthMultiplier * 620), (int) (heightMultiplier * 520));
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 24, 10, 24);

        float labelSize = Math.max(18f, (float) (18 * heightMultiplier));
        float inputSize = Math.max(16f, (float) (16 * heightMultiplier));
        float btnSize = Math.max(16f, (float) (16 * heightMultiplier));

        JLabel nameLbl = new JLabel("File Name:");
        nameLbl.setFont(plainMainFont.deriveFont(Font.BOLD, labelSize));
        nameLbl.setForeground(Color.BLACK);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.add(nameLbl, gbc);

        JTextField tf = buildTextField(noExt, inputSize, "Type file name here...");
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        dialog.add(tf, gbc);

        JLabel catLbl = new JLabel("Category Folder:");
        catLbl.setFont(plainMainFont.deriveFont(Font.BOLD, labelSize));
        catLbl.setForeground(Color.BLACK);
        gbc.gridy = 2;
        gbc.weightx = 0;
        dialog.add(catLbl, gbc);

        JComboBox<String> catBox = new JComboBox<>(Fetcher.getFolderName());
        catBox.setEditable(true);
        catBox.setFont(plainMainFont.deriveFont(Font.PLAIN, inputSize));
        catBox.setBackground(Color.WHITE);
        catBox.setForeground(Color.BLACK);
        if (!currentCategory.isEmpty()) catBox.setSelectedItem(currentCategory);
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        dialog.add(catBox, gbc);

        JLabel subLbl = new JLabel("Sub-folder (Record Name):");
        subLbl.setFont(plainMainFont.deriveFont(Font.BOLD, labelSize));
        subLbl.setForeground(Color.BLACK);
        gbc.gridy = 4;
        gbc.weightx = 0;
        dialog.add(subLbl, gbc);

        JComboBox<String> subBox = new JComboBox<>(
                currentCategory.isEmpty() ? new String[0] : Fetcher.getSubFolders(currentCategory));
        subBox.setEditable(true);
        subBox.setFont(plainMainFont.deriveFont(Font.PLAIN, inputSize));
        subBox.setBackground(Color.WHITE);
        subBox.setForeground(Color.BLACK);
        if (!currentSubFolder.isEmpty()) subBox.setSelectedItem(currentSubFolder);
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        dialog.add(subBox, gbc);

        catBox.addActionListener(e -> {
            String cat = FileHandler.resolveCombo(catBox);
            subBox.removeAllItems();
            if (cat != null) for (String s : Fetcher.getSubFolders(cat)) subBox.addItem(s);
        });

        JButton uploadBtn = UI.buttonDesign();
        uploadBtn.setText("Upload");
        uploadBtn.setFont(plainMainFont.deriveFont(Font.BOLD, btnSize));
        uploadBtn.setEnabled(!noExt.isEmpty());

        uploadBtn.addActionListener(e -> {
            try {
                File dest = FileHandler.moveFiles(file, catBox, subBox, tf, dialog);
                if (dest != null) {
                    String cat = FileHandler.resolveCombo(catBox);
                    String sub = FileHandler.resolveCombo(subBox);
                    if (cat != null && sub != null) {
                        Writer.appendToMetadata(cat, sub, dest.getName());
                        ManifestManager.markFolderScanned(cat);
                        Writer.logUpload(cat, sub, dest.getName());
                    }
                    defaultDir[0] = file.getParent();
                    refreshFolderButtons(container, heightMultiplier, dataPane);
                    if (cat != null && sub != null) showFiles(cat, sub, dataPane, 0);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Upload failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                check();
            }

            public void removeUpdate(DocumentEvent e) {
                check();
            }

            public void insertUpdate(DocumentEvent e) {
                check();
            }

            public void check() {
                uploadBtn.setEnabled(!tf.getText().trim().isEmpty());
            }
        });

        gbc.gridy = 6;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 24, 16, 24);
        dialog.add(uploadBtn, gbc);
        dialog.setVisible(true);
    }

    // =========================================================================
    // Filesystem operations
    // =========================================================================
    private void renameSubFolder(String category, String oldName, String newName) {
        File base = new File(SFADSMS + File.separator + ".data" + File.separator + category);
        File oldDir = new File(base, oldName);
        File newDir = new File(base, newName);
        if (oldDir.exists() && !newDir.exists()) {
            if (!oldDir.renameTo(newDir)) {
                showError("Could not rename folder on disk.");
                return;
            }
        }
        Writer.logRenameSubFolder(category, oldName, newName);
    }

    private void trashSubFolder(String category, String subFolderName) {
        File dir = new File(SFADSMS + File.separator + ".data"
                + File.separator + category + File.separator + subFolderName);
        if (!dir.exists()) return;
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH))
            Desktop.getDesktop().moveToTrash(dir);
        else deleteRecursive(dir);
        Writer.logDeleteSubFolder(category, subFolderName);
    }

    private void trashFile(String category, String subFolder, String fileName) {
        File f = new File(SFADSMS + File.separator + ".data"
                + File.separator + category + File.separator + subFolder
                + File.separator + fileName);
        if (!f.exists()) return;
        boolean moved = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                && Desktop.getDesktop().moveToTrash(f);
        if (!moved) {
            int c = JOptionPane.showConfirmDialog(this, "Recycle Bin unavailable. Permanently delete?",
                    "Trash Unavailable", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c == JOptionPane.YES_OPTION) f.delete();
            else return;
        }
        Writer.logDeleteFile(category, subFolder, fileName);
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) for (File c : f.listFiles()) deleteRecursive(c);
        f.delete();
    }

    // =========================================================================
    // Sidebar — plain buttons, right-click popup for management
    // =========================================================================
    public void refreshFolderButtons(Container container, double hm, JScrollPane... panes) {
        container.removeAll();
        Writer.updateAllChangedFolders();
        String[] allFolders = Fetcher.getFolderName();

        ArrayList<String> folders = new ArrayList<>();
        for (String f : allFolders)
            if (currentSearchQuery.isEmpty()
                    || f.toLowerCase().contains(currentSearchQuery.toLowerCase()))
                folders.add(f);

        for (String name : folders) {
            JButton btn = UI.getButton(hm, name);

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(mainColor);
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, btn.getPreferredSize().height));
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(btn, BorderLayout.CENTER);

            btn.addActionListener(e -> {
                if (panes.length > 0) showSubFolders(name, panes[0], 0);
            });

            btn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger()) showCategoryMenu(btn, name, container, panes);
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger()) showCategoryMenu(btn, name, container, panes);
                }
            });

            container.add(wrapper);
        }

        container.revalidate();
        container.repaint();

        if (currentSearchQuery.isEmpty() && !folders.isEmpty()
                && panes.length > 0 && container.getComponentCount() > 0) {
            Component first = container.getComponent(0);
            if (first instanceof JPanel wp && wp.getComponentCount() > 0
                    && wp.getComponent(0) instanceof JButton btn0) {
                btn0.doClick();
            }
        }
    }

    private void showCategoryMenu(Component invoker, String category,
                                  Container container, JScrollPane... panes) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameItem = new JMenuItem("✏  Rename category");
        JMenuItem deleteItem = new JMenuItem("🗑  Delete category");
        JMenuItem sep1 = new JMenuItem("─────────────────");
        JMenuItem addUser = new JMenuItem("➕  Add admin user");
        JMenuItem removeUser = new JMenuItem("➖  Remove admin user");
        JMenuItem changeCred = new JMenuItem("🔑  Change my credentials");
        JMenuItem showUsers = new JMenuItem("👥  View all users");

        sep1.setEnabled(false);

        renameItem.addActionListener(e -> showRenameCategoryDialog(category, container, panes));
        deleteItem.addActionListener(e -> confirmDeleteCategory(category, container, panes));
        addUser.addActionListener(e -> Auth.addUser(this));
        removeUser.addActionListener(e -> Auth.removeUser(this));
        changeCred.addActionListener(e -> Auth.changeCredentials(this));
        showUsers.addActionListener(e -> Auth.showUsers(this));

        menu.add(renameItem);
        menu.add(deleteItem);
        menu.add(sep1);
        menu.add(addUser);
        menu.add(removeUser);
        menu.add(changeCred);
        menu.add(showUsers);
        menu.show(invoker, 0, invoker.getHeight());
    }

    private void showRenameCategoryDialog(String oldName,
                                          Container container, JScrollPane... panes) {
        JTextField tf = new JTextField(oldName, 22);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.add(new JLabel("New name for \"" + oldName + "\":"), BorderLayout.NORTH);
        panel.add(tf, BorderLayout.CENTER);
        SwingUtilities.invokeLater(tf::selectAll);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Rename Category", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String newName = tf.getText().trim();
        if (newName.isEmpty() || newName.equals(oldName)) return;

        File oldDir = new File(SFADSMS + File.separator + ".data" + File.separator + oldName);
        File newDir = new File(SFADSMS + File.separator + ".data" + File.separator + newName);
        if (newDir.exists()) {
            showError("A category named \"" + newName + "\" already exists.");
            return;
        }
        if (!oldDir.renameTo(newDir)) {
            showError("Could not rename category on disk.");
            return;
        }

        ManifestManager.renameCategory(oldName, newName);
        Writer.logRenameCategory(oldName, newName);

        if (currentCategory.equals(oldName)) {
            currentCategory = "";
            currentSubFolder = "";
            dataPane.setViewportView(new JPanel());
        }
        refreshFolderButtons(container, heightMultiplier, panes);
    }

    private void confirmDeleteCategory(String category,
                                       Container container, JScrollPane... panes) {
        String msg = "<html><body style='width:300px'>"
                + "Permanently remove category <b>" + category + "</b>?<br><br>"
                + "All sub-folders and files will be sent to the <b>Recycle Bin</b>."
                + "</body></html>";
        int c = JOptionPane.showConfirmDialog(this, msg,
                "Delete Category", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;

        File dir = new File(SFADSMS + File.separator + ".data" + File.separator + category);
        if (dir.exists()) {
            boolean moved = Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                    && Desktop.getDesktop().moveToTrash(dir);
            if (!moved) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Recycle Bin unavailable. Permanently delete?",
                        "Trash Unavailable", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                deleteRecursive(dir);
            }
        }

        ManifestManager.removeCategory(category);
        Writer.logDeleteCategory(category);

        if (currentCategory.equals(category)) {
            currentCategory = "";
            currentSubFolder = "";
            dataPane.setViewportView(new JPanel());
        }
        refreshFolderButtons(container, heightMultiplier, panes);
    }

    // =========================================================================
    // Data area shell
    // =========================================================================
    public JScrollPane dataArea() {
        JScrollPane jsp = new JScrollPane(new JPanel());
        jsp.setBorder(null);
        jsp.getViewport().setBackground(Color.WHITE);
        return jsp;
    }

    // =========================================================================
    // Pagination builder
    // =========================================================================
    private JPanel buildPagination(float pFont, int page, int totalPages,
                                   int totalRows, java.util.function.IntConsumer goTo) {
        JPanel pp = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        pp.setBackground(Color.WHITE);

        JButton first = paginationBtn("<<", pFont);
        first.setEnabled(page > 0);
        JButton prev = paginationBtn("‹ Prev", pFont);
        prev.setEnabled(page > 0);
        JButton next = paginationBtn("Next ›", pFont);
        next.setEnabled(page < totalPages - 1);
        JButton last = paginationBtn(">>", pFont);
        last.setEnabled(page < totalPages - 1);

        first.addActionListener(e -> goTo.accept(0));
        int fp1 = page;
        prev.addActionListener(e -> goTo.accept(fp1 - 1));
        int fp = page;
        next.addActionListener(e -> goTo.accept(fp + 1));
        last.addActionListener(e -> goTo.accept(totalPages - 1));

        JLabel pgLabel = new JLabel("Page " + (page + 1) + " of " + totalPages
                + "   (" + totalRows + " items)");
        pgLabel.setFont(plainMainFont.deriveFont(Font.PLAIN, pFont));
        pgLabel.setForeground(Color.BLACK);

        pp.add(first);
        pp.add(prev);
        pp.add(pgLabel);
        pp.add(next);
        pp.add(last);
        return pp;
    }

    private JButton paginationBtn(String text, float fs) {
        JButton b = new JButton(text);
        b.setFont(plainMainFont.deriveFont(Font.PLAIN, fs));
        b.setForeground(Color.BLACK);
        b.setFocusPainted(false);
        return b;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
    }

    // =========================================================================
    // Sub-folder actions — Edit + Move + Delete
    // =========================================================================
    private class SubFolderActionRenderer implements TableCellRenderer {
        private final float fs;

        SubFolderActionRenderer(float fs) {
            this.fs = fs;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            return buildSubFolderPanel(fs, false, null, null, null, null);
        }
    }

    private class SubFolderActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final float fs;
        private final String category;
        private final JScrollPane scroll;
        private final int pg;
        private String subFolderName;

        SubFolderActionEditor(float fs, String category, JScrollPane scroll, int pg) {
            this.fs = fs;
            this.category = category;
            this.scroll = scroll;
            this.pg = pg;
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public Object getCellEditorValue() {
            return subFolderName;
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable t, Object value, boolean sel, int row, int col) {
            subFolderName = (String) value;
            return buildSubFolderPanel(fs, true, subFolderName,
                    this::onEdit, this::onMove, this::onDelete);
        }

        private void onEdit() {
            stopCellEditing();
            showRenameSubFolderDialog(category, subFolderName, scroll, pg);
        }

        private void onMove() {
            stopCellEditing();

            String[] allCats = Fetcher.getFolderName();
            String CREATE_CAT = "[ + Create new category ]";

            String[] catOptions = new String[allCats.length + 1];
            System.arraycopy(allCats, 0, catOptions, 0, allCats.length);
            catOptions[allCats.length] = CREATE_CAT;

            String dest = (String) JOptionPane.showInputDialog(Main.this,
                    "Move \"" + subFolderName + "\" to which category?",
                    "Move Sub-folder", JOptionPane.PLAIN_MESSAGE, null,
                    catOptions, catOptions[0]);
            if (dest == null) return;

            if (dest.equals(CREATE_CAT)) {
                dest = JOptionPane.showInputDialog(Main.this,
                        "Enter new category name:", "New Category", JOptionPane.PLAIN_MESSAGE);
                if (dest == null || dest.isBlank()) return;
                dest = dest.trim();
                // Create the directory so it shows up in sidebar immediately
                File newCatDir = new File(SFADSMS + File.separator + ".data" + File.separator + dest);
                newCatDir.mkdirs();
                ManifestManager.markFolderChanged(dest);
            }

            if (dest.equals(category)) {
                showInfo("Same location", "\"" + subFolderName + "\" is already in this category.");
                return;
            }

            File src = new File(SFADSMS + File.separator + ".data"
                    + File.separator + category + File.separator + subFolderName);
            File dst = new File(SFADSMS + File.separator + ".data"
                    + File.separator + dest + File.separator + subFolderName);

            if (dst.exists()) {
                showError("A folder named \"" + subFolderName + "\" already exists in \"" + dest + "\".");
                return;
            }
            if (!src.renameTo(dst)) {
                showError("Could not move folder. Both categories must be on the same drive.");
                return;
            }

            ManifestManager.markFolderChanged(dest);
            Writer.logMoveSubFolder(Auth.currentUser(), category, subFolderName, dest);
            refreshFolderButtons(sidebarContainer, heightMultiplier, dataPane);
            showSubFolders(category, scroll, pg);
        }

        private void onDelete() {
            stopCellEditing();
            String msg = "<html><body style='width:280px'>"
                    + "Delete folder <b>" + subFolderName + "</b> and all its files?<br><br>"
                    + "This will send everything to the <b>Recycle Bin</b>."
                    + "</body></html>";
            int c = JOptionPane.showConfirmDialog(Main.this, msg,
                    "Move to Recycle Bin?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c == JOptionPane.YES_OPTION) {
                trashSubFolder(category, subFolderName);
                showSubFolders(category, scroll, pg);
            }
        }
    }

    // =========================================================================
    // File actions — View + Print + Move + Delete
    // =========================================================================
    private class FileActionRenderer implements TableCellRenderer {
        private final float fs;

        FileActionRenderer(float fs) {
            this.fs = fs;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            return buildFilePanel(fs, false, null, null, null, null, null, null);
        }
    }

    private class FileActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final float fs;
        private final String category;
        private final String subFolder;
        private final JScrollPane scroll;
        private final int pg;
        private String fileName;

        FileActionEditor(float fs, String category, String subFolder,
                         JScrollPane scroll, int pg) {
            this.fs = fs;
            this.category = category;
            this.subFolder = subFolder;
            this.scroll = scroll;
            this.pg = pg;
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public Object getCellEditorValue() {
            return fileName;
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable t, Object value, boolean sel, int row, int col) {
            fileName = (String) value;
            return buildFilePanel(fs, true, fileName,
                    this::onView, this::onPrint, this::onRename, this::onMove, this::onDelete);
        }

        private File resolveFile() {
            File f = new File(SFADSMS + File.separator + ".data"
                    + File.separator + category + File.separator + subFolder
                    + File.separator + fileName);
            if (!f.exists()) {
                showInfo("Not found", "\"" + fileName + "\" not found on disk.");
                return null;
            }
            return f;
        }

        private void onView() {
            stopCellEditing();
            File f = resolveFile();
            if (f == null) return;
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                showInfo("Unsupported", "Your system cannot open files this way.");
                return;
            }
            try {
                Desktop.getDesktop().open(f);
            } catch (IOException ex) {
                showError("Could not open:\n" + ex.getMessage());
            }
        }

        private void onPrint() {
            stopCellEditing();
            File f = resolveFile();
            if (f == null) return;
            if (!Desktop.isDesktopSupported()) {
                showInfo("Unsupported", "Desktop not supported.");
                return;
            }
            String n = f.getName().toLowerCase();
            if (n.endsWith(".docx") || n.endsWith(".doc") || n.endsWith(".pdf")) {
                if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) return;
                try {
                    Desktop.getDesktop().open(f);
                } catch (IOException ex) {
                    showError("Could not open:\n" + ex.getMessage());
                }
            } else {
                if (!Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) return;
                try {
                    Desktop.getDesktop().print(f);
                } catch (IOException ex) {
                    showError("Could not print:\n" + ex.getMessage());
                }
            }
        }

        private void onMove() {
            stopCellEditing();

            // ── Step 1: pick or create a category ────────────────────────────
            String[] allCats = Fetcher.getFolderName();
            String CREATE_CAT = "[ + Create new category ]";

            // Build options: existing cats + create option
            String[] catOptions = new String[allCats.length + 1];
            System.arraycopy(allCats, 0, catOptions, 0, allCats.length);
            catOptions[allCats.length] = CREATE_CAT;

            String destCat = (String) JOptionPane.showInputDialog(Main.this,
                    "Move \"" + fileName + "\" to which category?",
                    "Move File — Step 1: Category",
                    JOptionPane.PLAIN_MESSAGE, null, catOptions,
                    category.isEmpty() ? (catOptions.length > 0 ? catOptions[0] : CREATE_CAT) : category);
            if (destCat == null) return;

            if (destCat.equals(CREATE_CAT)) {
                destCat = JOptionPane.showInputDialog(Main.this,
                        "Enter new category name:", "New Category", JOptionPane.PLAIN_MESSAGE);
                if (destCat == null || destCat.isBlank()) return;
                destCat = destCat.trim();
                // Create the category directory so it shows up in the sidebar
                File catDir = new File(SFADSMS + File.separator + ".data" + File.separator + destCat);
                catDir.mkdirs();
                ManifestManager.markFolderChanged(destCat);
            }

            // ── Step 2: pick or create a sub-folder ──────────────────────────
            String[] subs = Fetcher.getSubFolders(destCat);
            String CREATE_SUB = "[ + Create new sub-folder ]";

            String destSub;
            if (subs.length == 0) {
                // No subs yet — go straight to create
                destSub = JOptionPane.showInputDialog(Main.this,
                        "No sub-folders in \"" + destCat + "\" yet.\nEnter a new sub-folder name:",
                        "New Sub-folder", JOptionPane.PLAIN_MESSAGE);
                if (destSub == null || destSub.isBlank()) return;
                destSub = destSub.trim();
            } else {
                String[] subOptions = new String[subs.length + 1];
                System.arraycopy(subs, 0, subOptions, 0, subs.length);
                subOptions[subs.length] = CREATE_SUB;

                destSub = (String) JOptionPane.showInputDialog(Main.this,
                        "Move to which sub-folder inside \"" + destCat + "\"?",
                        "Move File — Step 2: Sub-folder",
                        JOptionPane.PLAIN_MESSAGE, null, subOptions, subOptions[0]);
                if (destSub == null) return;

                if (destSub.equals(CREATE_SUB)) {
                    destSub = JOptionPane.showInputDialog(Main.this,
                            "Enter new sub-folder name:", "New Sub-folder", JOptionPane.PLAIN_MESSAGE);
                    if (destSub == null || destSub.isBlank()) return;
                    destSub = destSub.trim();
                }
            }

            if (destCat.equals(category) && destSub.equals(subFolder)) {
                showInfo("Same location", "The file is already in this location.");
                return;
            }

            // ── Move the file ─────────────────────────────────────────────────
            File src = resolveFile();
            if (src == null) return;
            File destDir = new File(SFADSMS + File.separator + ".data"
                    + File.separator + destCat + File.separator + destSub);
            destDir.mkdirs();
            File dst = new File(destDir, fileName);

            if (dst.exists()) {
                showError("A file named \"" + fileName + "\" already exists there.");
                return;
            }
            if (!src.renameTo(dst)) {
                showError("Could not move file. Both locations must be on the same drive.");
                return;
            }

            Writer.removeFromMetadata(category, subFolder, fileName);
            Writer.appendToMetadata(destCat, destSub, fileName);
            ManifestManager.markFolderChanged(destCat);
            Writer.logMoveFile(Auth.currentUser(), category, subFolder, destCat, destSub, fileName);

            // Refresh sidebar in case a new category was created
            refreshFolderButtons(sidebarContainer, heightMultiplier, dataPane);
            showFiles(category, subFolder, scroll, pg);
        }

        private void onRename() {
            stopCellEditing();
            showRenameFileDialog(category, subFolder, fileName, scroll, pg);
        }

        private void onDelete() {
            stopCellEditing();
            String msg = "<html><body style='width:280px'>"
                    + "Send <b>" + fileName + "</b> to the Recycle Bin?<br><br>"
                    + "You can restore it from there if needed."
                    + "</body></html>";
            int c = JOptionPane.showConfirmDialog(Main.this, msg,
                    "Move to Recycle Bin?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c == JOptionPane.YES_OPTION) {
                trashFile(category, subFolder, fileName);
                showFiles(category, subFolder, scroll, pg);
            }
        }
    }

    // =========================================================================
    // Rename file dialog
    // =========================================================================
    private void showRenameFileDialog(String category, String subFolder,
                                      String oldFileName, JScrollPane scrollPane, int page) {
        // Separate base name from extension so user only types the display name
        int dotIdx = oldFileName.lastIndexOf('.');
        String oldBase = dotIdx > 0 ? oldFileName.substring(0, dotIdx) : oldFileName;
        String ext     = dotIdx > 0 ? oldFileName.substring(dotIdx)    : "";

        JDialog dialog = new JDialog(this, "Rename File", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setResizable(false);
        dialog.setSize((int) (widthMultiplier * 520), (int) (heightMultiplier * 280));
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(Color.WHITE);

        float labelSize = Math.max(18f, (float) (18 * heightMultiplier));
        float hintSize  = Math.max(13f, (float) (13 * heightMultiplier));
        float inputSize = Math.max(16f, (float) (16 * heightMultiplier));
        float btnSize   = Math.max(16f, (float) (16 * heightMultiplier));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 24, 6, 24);

        JLabel titleLbl = new JLabel("Rename File:");
        titleLbl.setFont(plainMainFont.deriveFont(Font.BOLD, labelSize));
        titleLbl.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        dialog.add(titleLbl, gbc);

        JLabel hintLbl = new JLabel("Current:  " + oldFileName);
        hintLbl.setFont(plainMainFont.deriveFont(Font.ITALIC, hintSize));
        hintLbl.setForeground(new Color(100, 100, 100));
        gbc.gridy = 1;
        dialog.add(hintLbl, gbc);

        if (!ext.isEmpty()) {
            JLabel extLbl = new JLabel("Extension  \"" + ext + "\"  will be kept automatically.");
            extLbl.setFont(plainMainFont.deriveFont(Font.ITALIC, hintSize));
            extLbl.setForeground(new Color(130, 130, 130));
            gbc.gridy = 2;
            dialog.add(extLbl, gbc);
        }

        JTextField tf = buildTextField(oldBase, inputSize, "Enter new file name...");
        gbc.gridy = 3; gbc.weightx = 1.0;
        dialog.add(tf, gbc);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setBackground(Color.WHITE);
        JButton saveBtn   = actionBtn("Save",   new Color(39, 174, 96),   btnSize);
        JButton cancelBtn = actionBtn("Cancel", new Color(130, 130, 130), btnSize);

        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { check(); }
            public void removeUpdate(DocumentEvent e)  { check(); }
            public void insertUpdate(DocumentEvent e)  { check(); }
            private void check() { saveBtn.setEnabled(!tf.getText().trim().isEmpty()); }
        });

        saveBtn.addActionListener(e -> {
            String newBase = tf.getText().trim();
            if (newBase.isEmpty() || newBase.equals(oldBase)) { dialog.dispose(); return; }
            String newFileName = newBase + ext;
            renameFile(category, subFolder, oldFileName, newFileName);
            dialog.dispose();
            showFiles(category, subFolder, scrollPane, page);
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        btnRow.add(saveBtn); btnRow.add(cancelBtn);
        gbc.gridy = 4; gbc.weightx = 0; gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 24, 16, 24);
        dialog.add(btnRow, gbc);
        tf.selectAll();
        dialog.setVisible(true);
    }

    private void renameFile(String category, String subFolder,
                            String oldFileName, String newFileName) {
        File dir    = new File(SFADSMS + File.separator + ".data"
                + File.separator + category + File.separator + subFolder);
        File oldFile = new File(dir, oldFileName);
        File newFile = new File(dir, newFileName);

        if (!oldFile.exists()) { showError("File not found on disk."); return; }
        if (newFile.exists())  { showError("A file named \"" + newFileName + "\" already exists here."); return; }
        if (!oldFile.renameTo(newFile)) { showError("Could not rename file on disk."); return; }

        // Keep metadata in sync — remove old entry, add new one
        Writer.removeFromMetadata(category, subFolder, oldFileName);
        Writer.appendToMetadata(category, subFolder, newFileName);
        ManifestManager.markFolderChanged(category);
        Writer.logRenameFile(Auth.currentUser(), category, subFolder, oldFileName, newFileName);
    }
}