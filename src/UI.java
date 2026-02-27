import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

public class UI {
    static void addGBComponent(JPanel p, Component c, int x, int y, int w, int h,
                               double weightx, double weighty, int fill, int anchor, Dimension dimension, int... insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = w;
        gbc.gridheight = h;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.fill = fill;

        int top = 0, left = 0, bottom = 0, right = 0;

        if (insets.length >= 1) top = insets[0];
        if (insets.length >= 2) left = insets[1];
        if (insets.length >= 3) bottom = insets[2];
        if (insets.length >= 4) right = insets[3];
        c.setPreferredSize(dimension);
        c.setMinimumSize(dimension);
        gbc.anchor = anchor;
        gbc.insets = new Insets(top, left, bottom, right);
        p.add(c, gbc);
    }
    static JButton buttonDesign(){
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2d.setColor(Main.mainColor.darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(Main.mainColor.brighter());
                } else {
                    g2d.setColor(Main.mainColor);
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 60);
                super.paintComponent(g);
                g2d.dispose();
            }
        };
        button.setForeground(Color.white);
        button.setCursor(Main.HAND);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        return button;
    }
    static JButton getButton(double heightMultiplier, String folderButtons) {
        JButton button = new JButton(folderButtons);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int)(heightMultiplier * 60)));
        button.setPreferredSize(new Dimension(0, (int)(heightMultiplier * 60)));
        button.setMinimumSize(new Dimension(50, (int)(heightMultiplier * 60)));
        button.setFont(Main.plainMainFont.deriveFont(Font.BOLD, (float) (heightMultiplier * 20f)));
        button.setForeground(Color.white);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setBackground(Main.mainColor);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusable(false);
        button.setBorderPainted(false);
        return button;
    }
    static void loadCustomFont() {
        try {
            File fontLoc = new File(Main.filePath + File.separator + "src" + File.separator + "res" + File.separator + "fonts" + File.separator + "Montserrat-ExtraBold.ttf");
            Main.mainFont = Font.createFont(Font.TRUETYPE_FONT, fontLoc);
        } catch (Exception e) {
            System.out.println("File not found");
            Main.mainFont = new Font("Arial", Font.BOLD, 24);
        }
    }
    static void loadCustomPlainFont() {
        try {
            File fontLoc = new File(Main.filePath + File.separator + "src" + File.separator + "res" + File.separator + "fonts" + File.separator + "Montserrat-VariableFont_wght.ttf");
            Main.plainMainFont = Font.createFont(Font.TRUETYPE_FONT, fontLoc);
        } catch (Exception e) {
            System.out.println("File not found");
            Main.plainMainFont = new Font("Arial", Font.BOLD, 24);
        }
    }
    static JPanel getPanel(double heightMultiplier, double widthMultiplier) {
        JPanel sortArea = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D graphics2D = (Graphics2D) g;
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setFont(Main.plainMainFont.deriveFont(Font.BOLD, (float)(heightMultiplier * 20f)));
                String sort = "Sort:";
                FontMetrics fontMetrics = graphics2D.getFontMetrics();
                int x = (int) (widthMultiplier * 70);
                int y = ((getHeight() + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2);
                graphics2D.drawString(sort, x, y);

            }
        };
        sortArea.setBackground(Main.backGround);
        return sortArea;
    }

    static JTextField getJTextField() {
        JTextField search = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D graphics2D = (Graphics2D)g.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                graphics2D.setColor(Color.WHITE);
                graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 60);

                // 2. Draw the Magnifying Glass (The Icon)
                int iconAreaWidth = 45;
                int separatorX = getWidth() - iconAreaWidth;

                graphics2D.setColor(new Color(200, 200, 200));
                graphics2D.drawLine(separatorX, 10, separatorX, getHeight() - 10);

                // 3. Magnifying Glass
                graphics2D.setColor(Color.GRAY);
                graphics2D.setStroke(new BasicStroke(2));
                int centerX = separatorX + (iconAreaWidth / 2) - 5;
                int centerY = (getHeight() / 2) - 5;
                graphics2D.drawOval(centerX, centerY, 10, 10);
                graphics2D.drawLine(centerX + 8, centerY + 8, centerX + 13, centerY + 13);

                if (getText().isEmpty()) {
                    graphics2D.setFont(getFont().deriveFont(Font.PLAIN));
                    graphics2D.setColor(new Color(170, 170, 170));
                    FontMetrics fm = graphics2D.getFontMetrics();
                    int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    graphics2D.drawString("Search folders...", 15, textY);
                }
                graphics2D.dispose();
                super.paintComponent(g);
            }
        };
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && search.getText().isEmpty())e.consume();
            }
        });
        search.setOpaque(false);
        search.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        return search;
    }

    static JPanel getJPanel(double heightMultiplier, double widthMultiplier) {
        JPanel topSide = new JPanel() {

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (Main.img == null) return;

                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int panelHeight = getHeight();
                int panelWidth = getWidth();


                double imgWidth = Main.img.getWidth(this);
                double imgHeight = Main.img.getHeight(this);
                double aspectRatio = imgWidth / imgHeight;

                int logoH = (int) (panelHeight * 0.7);
                int logoW = (int) (logoH * aspectRatio);

                //Fall back
                if (logoW > panelWidth * 0.9) {
                    logoW = (int) (panelWidth * 0.9);
                    logoH = (int) (logoW / aspectRatio);
                }

                int logoX = (panelWidth - logoW) / 2;
                int logoY = (int) ((panelHeight - logoH) / 4);
                g2d.drawImage(Main.img, logoX, logoY, logoW, logoH, this);
                g2d.setColor(Color.white);
                g2d.setFont(Main.mainFont.deriveFont(Font.BOLD, (float) (heightMultiplier * 25f)));
                FontMetrics fm1 = g2d.getFontMetrics();
                String label1 = "SFADSMS";

                int textX1 = (panelWidth - fm1.stringWidth(label1)) / 2;
                int padding = (int)(15 * heightMultiplier);
                int textY1 = logoY + logoH + padding + fm1.getAscent();

                g2d.drawString(label1, textX1, textY1);
                g2d.setFont(Main.plainMainFont.deriveFont(Font.BOLD, (float) (heightMultiplier * 20f))); // Smaller and thinner
                FontMetrics fm2 = g2d.getFontMetrics();
                String label2 = "FORMS";

                int textX2 = (panelWidth - fm2.stringWidth(label2)) / 2;
                // Math: Start at the baseline of Line 1 + Descent of Line 1 + Ascent of Line 2 + Gap
                int gapBetweenLines = (int)(5 * heightMultiplier);
                int textY2 = textY1 + fm1.getDescent() + fm2.getAscent() + gapBetweenLines;

                g2d.drawString(label2, textX2, textY2);
            }
        };
        topSide.setBackground(Main.mainColor);
        topSide.setMinimumSize(new Dimension((int) (280 * widthMultiplier), (int) (350 * heightMultiplier)));
        topSide.setPreferredSize(new Dimension((int) (280 * widthMultiplier), (int) (350 * heightMultiplier)));
        return topSide;
    }

    static JPanel getJPanel(double heightMultiplier) {
        int textOffsetLeft = 25; // Increase to push text right, decrease to pull left
        int textOffsetTop = 5;   // Increase to push text down, decrease to pull up

        JPanel topArea = new JPanel() {

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;

                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int panelHeight = getHeight();

                int logoH = (int)(getHeight() * 0.6);
                int logoX = (int)(getWidth() * 0.02);
                int logoY = (panelHeight - logoH) / 2;

                g2d.drawImage(Main.icon.getImage(), logoX, logoY, logoH, logoH, this);

                String schoolName = "F. DAVID ELEMENTARY SCHOOL";
                String systemDescription = "SCHOOL FILING AND DATA STORAGE MANAGEMENT SYSTEM";

                g2d.setColor(Color.white);
                g2d.setFont(Main.mainFont.deriveFont(Font.BOLD, (float) (heightMultiplier * 40f)));
                FontMetrics fontMetrics = g2d.getFontMetrics();

                int xPos = logoX + logoH + textOffsetLeft;
                int yPos = logoY + fontMetrics.getAscent() + textOffsetTop;

                g2d.drawString(schoolName, xPos, yPos);
                g2d.setFont(Main.mainFont.deriveFont(Font.BOLD, (float) (heightMultiplier * 20f)));
                int subTextY = yPos + fontMetrics.getDescent() + (int)(heightMultiplier * 20);

                g2d.drawString(systemDescription, xPos, subTextY);
            }
        };
        topArea.setBackground(Main.mainColor);
        return topArea;
    }

}
