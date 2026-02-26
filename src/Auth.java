import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-user admin authentication.
 * <p>
 * auth.dat: one line per admin → username|sha256hash
 * <p>
 * A single login at program startup sets the session user.
 * All log lines use the session username.
 * Destructive ops no longer re-prompt for password (already authenticated).
 */
public class Auth {

    private static final String AUTH_FILE = System.getProperty("user.home") + File.separator + ".SFADSMS" + File.separator + "auth.dat";

    // Currently logged-in username (set by showLoginDialog)
    private static String sessionUser = null;

    // ── Session ───────────────────────────────────────────────────────────────

    /**
     * Returns the logged-in username, or "unknown" if not yet set.
     */
    static String currentUser() {
        return sessionUser != null ? sessionUser : "unknown";
    }

    /**
     * True if at least one admin account exists.
     */
    static boolean hasAnyUser() {
        return !loadAll().isEmpty();
    }

    /**
     * Shows the startup login (or first-time setup) dialog.
     * Blocks until successful login. Returns false if the user cancels
     * (caller should exit the application).
     */
    static boolean showLoginDialog(Component parent) {
        if (!hasAnyUser()) {
            // First run — create the very first admin account
            return firstTimeSetup(parent);
        }

        while (true) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 10, 6, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;

            JLabel title = new JLabel("Admin Login");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            title.setHorizontalAlignment(SwingConstants.CENTER);
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            panel.add(title, gbc);
            gbc.gridwidth = 1;

            JLabel lUser = new JLabel("Username:");
            gbc.gridy = 1;
            panel.add(lUser, gbc);
            JTextField userField = new JTextField(18);
            gbc.gridy = 2;
            panel.add(userField, gbc);

            JLabel lPass = new JLabel("Password:");
            gbc.gridy = 3;
            panel.add(lPass, gbc);
            JPasswordField pf = new JPasswordField(18);
            gbc.gridy = 4;
            panel.add(pf, gbc);

            SwingUtilities.invokeLater(userField::requestFocusInWindow);

            String[] options = {"Login", "Cancel"};
            int result = JOptionPane.showOptionDialog(parent, panel, "SFADSMS — Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

            if (result != 0) return false; // user hit Cancel / closed

            String username = userField.getText().trim();
            String password = new String(pf.getPassword());

            if (checkCredentials(username, password)) {
                sessionUser = username;
                return true;
            }

            JOptionPane.showMessageDialog(parent, "Incorrect username or password. Please try again.", "Login Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── User management (accessible from the UI) ──────────────────────────────

    /**
     * Add a new admin user. Requires the currently logged-in user's password to confirm.
     */
    static void addUser(Component parent) {
        if (!confirmCurrentPassword(parent, "add a new admin user")) return;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;

        JLabel lbl0 = new JLabel("New Username:");
        gbc.gridy = 0;
        panel.add(lbl0, gbc);
        JTextField newUser = new JTextField(18);
        gbc.gridy = 1;
        panel.add(newUser, gbc);

        JLabel lbl1 = new JLabel("New Password:");
        gbc.gridy = 2;
        panel.add(lbl1, gbc);
        JPasswordField pf1 = new JPasswordField(18);
        gbc.gridy = 3;
        panel.add(pf1, gbc);

        JLabel lbl2 = new JLabel("Confirm Password:");
        gbc.gridy = 4;
        panel.add(lbl2, gbc);
        JPasswordField pf2 = new JPasswordField(18);
        gbc.gridy = 5;
        panel.add(pf2, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Add Admin User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String username = newUser.getText().trim();
        String p1 = new String(pf1.getPassword());
        String p2 = new String(pf2.getPassword());

        if (username.isEmpty()) {
            err(parent, "Username cannot be empty.");
            return;
        }
        if (p1.isEmpty()) {
            err(parent, "Password cannot be empty.");
            return;
        }
        if (!p1.equals(p2)) {
            err(parent, "Passwords do not match.");
            return;
        }

        Map<String, String> users = loadAll();
        // Check case-insensitively so "IT" and "it" are treated as the same user
        boolean exists = users.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(username));
        if (exists) {
            err(parent, "A user named \"" + username + "\" already exists.");
            return;
        }
        // Only add once, with original display-case name
        users.put(username, hash(p1));
        flushAll(users);
        Writer.logUserAdded(sessionUser, username);
        JOptionPane.showMessageDialog(parent, "User \"" + username + "\" added successfully.", "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Remove an admin user. Cannot remove yourself. Requires your password.
     */
    static void removeUser(Component parent) {
        List<String> names = getUsernames();
        if (names.isEmpty()) {
            err(parent, "No users registered.");
            return;
        }

        String[] arr = names.toArray(new String[0]);
        String target = (String) JOptionPane.showInputDialog(parent, "Select user to remove:", "Remove Admin User", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
        if (target == null) return;
        if (target.equalsIgnoreCase(sessionUser)) {
            err(parent, "You cannot remove your own account.");
            return;
        }
        if (names.size() == 1) {
            err(parent, "Cannot remove the only admin account.");
            return;
        }
        if (!confirmCurrentPassword(parent, "remove user \"" + target + "\"")) return;

        Map<String, String> users = loadAll();
        users.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(target));
        flushAll(users);
        Writer.logUserRemoved(sessionUser, target);
        JOptionPane.showMessageDialog(parent, "User \"" + target + "\" removed.", "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Change your own password (and optionally your display username).
     */
    static void changeCredentials(Component parent) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;

        JLabel info = new JLabel("Logged in as: " + sessionUser);
        info.setForeground(Color.DARK_GRAY);
        gbc.gridy = 0;
        panel.add(info, gbc);

        JLabel l0 = new JLabel("New Username (blank = keep):");
        gbc.gridy = 1;
        panel.add(l0, gbc);
        JTextField newUser = new JTextField(18);
        gbc.gridy = 2;
        panel.add(newUser, gbc);

        JLabel l1 = new JLabel("Current Password:");
        gbc.gridy = 3;
        panel.add(l1, gbc);
        JPasswordField cur = new JPasswordField(18);
        gbc.gridy = 4;
        panel.add(cur, gbc);

        JLabel l2 = new JLabel("New Password:");
        gbc.gridy = 5;
        panel.add(l2, gbc);
        JPasswordField pf1 = new JPasswordField(18);
        gbc.gridy = 6;
        panel.add(pf1, gbc);

        JLabel l3 = new JLabel("Confirm New:");
        gbc.gridy = 7;
        panel.add(l3, gbc);
        JPasswordField pf2 = new JPasswordField(18);
        gbc.gridy = 8;
        panel.add(pf2, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Change My Credentials", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        if (!checkCredentials(sessionUser, new String(cur.getPassword()))) {
            err(parent, "Current password is incorrect.");
            return;
        }

        String desiredName = newUser.getText().trim();
        String finalName = desiredName.isEmpty() ? sessionUser : desiredName;
        String n1 = new String(pf1.getPassword());
        String n2 = new String(pf2.getPassword());

        if (n1.isEmpty() || !n1.equals(n2)) {
            err(parent, "New passwords do not match or are empty.");
            return;
        }

        // Remove old entry, add updated entry
        Map<String, String> users = loadAll();
        users.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(sessionUser));
        users.put(finalName, hash(n1));
        flushAll(users);
        String oldName = sessionUser;
        sessionUser = finalName;
        Writer.logCredentialsChanged(sessionUser, oldName, finalName);
        JOptionPane.showMessageDialog(parent, "Credentials updated. Now logged in as \"" + finalName + "\".", "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Show all registered admin usernames.
     */
    static void showUsers(Component parent) {
        List<String> names = getUsernames();
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No admin users registered.", "Users", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder("<html><body style='width:240px'><b>Registered admins:</b><br>");
        for (String n : names) {
            sb.append("• ").append(n);
            if (n.equalsIgnoreCase(sessionUser)) sb.append(" <i>(you)</i>");
            sb.append("<br>");
        }
        sb.append("</body></html>");
        JOptionPane.showMessageDialog(parent, sb.toString(), "Admin Users", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static boolean firstTimeSetup(Component parent) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;

        JLabel info = new JLabel("<html><b>First time setup</b><br>Create your admin account.</html>");
        gbc.gridy = 0;
        panel.add(info, gbc);

        JLabel l0 = new JLabel("Username:");
        gbc.gridy = 1;
        panel.add(l0, gbc);
        JTextField userField = new JTextField(18);
        gbc.gridy = 2;
        panel.add(userField, gbc);

        JLabel l1 = new JLabel("Password:");
        gbc.gridy = 3;
        panel.add(l1, gbc);
        JPasswordField pf1 = new JPasswordField(18);
        gbc.gridy = 4;
        panel.add(pf1, gbc);

        JLabel l2 = new JLabel("Confirm Password:");
        gbc.gridy = 5;
        panel.add(l2, gbc);
        JPasswordField pf2 = new JPasswordField(18);
        gbc.gridy = 6;
        panel.add(pf2, gbc);

        SwingUtilities.invokeLater(userField::requestFocusInWindow);

        String[] opts = {"Create Account", "Exit"};
        int result = JOptionPane.showOptionDialog(parent, panel, "SFADSMS — First Setup", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (result != 0) return false;

        String username = userField.getText().trim();
        String p1 = new String(pf1.getPassword());
        String p2 = new String(pf2.getPassword());

        if (username.isEmpty()) {
            err(parent, "Username cannot be empty.");
            return firstTimeSetup(parent);
        }
        if (p1.isEmpty()) {
            err(parent, "Password cannot be empty.");
            return firstTimeSetup(parent);
        }
        if (!p1.equals(p2)) {
            err(parent, "Passwords do not match.");
            return firstTimeSetup(parent);
        }

        Map<String, String> users = new LinkedHashMap<>();
        users.put(username, hash(p1));
        flushAll(users);
        sessionUser = username;
        Writer.logUserAdded("system", username);
        return true;
    }

    /**
     * Asks the currently logged-in user to confirm their password before a management action.
     */
    private static boolean confirmCurrentPassword(Component parent, String action) {
        JPasswordField pf = new JPasswordField(18);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);
        panel.add(new JLabel("<html>Enter <b>" + sessionUser + "</b>'s password to " + action + ":</html>"), BorderLayout.NORTH);
        panel.add(pf, BorderLayout.CENTER);
        SwingUtilities.invokeLater(pf::requestFocusInWindow);

        int r = JOptionPane.showConfirmDialog(parent, panel, "Confirm Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        if (checkCredentials(sessionUser, new String(pf.getPassword()))) return true;
        err(parent, "Incorrect password.");
        return false;
    }

    static boolean checkCredentials(String username, String password) {
        Map<String, String> users = loadAll();
        for (Map.Entry<String, String> e : users.entrySet())
            if (e.getKey().equalsIgnoreCase(username) && e.getValue().equals(hash(password))) return true;
        return false;
    }

    /**
     * Returns display-name usernames (original case).
     */
    static List<String> getUsernames() {
        return new ArrayList<>(loadAll().keySet());
    }

    /**
     * Loads auth.dat → LinkedHashMap<displayName, hash>.
     */
    private static Map<String, String> loadAll() {
        File f = new File(AUTH_FILE);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        Map<String, String> map = new LinkedHashMap<>();
        if (!f.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) map.put(parts[0], parts[1]);
                else if (parts.length == 1 && !parts[0].isBlank())
                    map.put("admin", parts[0]); // legacy hash-only format
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    /**
     * Saves the given map to disk.
     */
    private static void flushAll(Map<String, String> users) {
        File f = new File(AUTH_FILE);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            for (Map.Entry<String, String> e : users.entrySet()) {
                bw.write(e.getKey() + "|" + e.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not write auth data: " + e.getMessage());
        }
    }

    /**
     * Helper: merge a new entry into existing users and flush.
     */
    private static void saveAll(Map<String, String> newEntry, Map<String, String> existing) {
        // newEntry has display-case key; existing may have lowercase keys — use existing as base
        // and just append/overwrite with newEntry
        for (Map.Entry<String, String> e : newEntry.entrySet())
            existing.put(e.getKey(), e.getValue());
        flushAll(existing);
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available");
        }
    }

    private static void err(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}