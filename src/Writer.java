import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles all disk writes: audit log and metadata .txt files.
 *
 * Log format (one line per event):
 *   [YYYY-MM-DD HH:mm:ss] [ACTION] user=<os-user> | <key=value pairs>
 *
 * Metadata file layout (two-level structure):
 *   .SFADSMS/.data/<category>/<subFolder>/<subFolder>data.txt
 *   Each line: index|filename|timestamp
 */
public class Writer {

    private static final String LOG = System.getProperty("user.home")
            + File.separator + ".SFADSMS"
            + File.separator + ".log"
            + File.separator + ".log.txt";

    private static final String DATA_ROOT = System.getProperty("user.home")
            + File.separator + ".SFADSMS"
            + File.separator + ".data";

    private static final DateTimeFormatter LOG_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Audit log ─────────────────────────────────────────────────────────────

    /**
     * Writes one structured line to the audit log.
     * Format: [timestamp] [ACTION] user=<name> | <details>
     *
     * @param action  e.g. "UPLOAD", "DELETE-FILE", "RENAME-SUBFOLDER"
     * @param details e.g. "category=Grade7 | subfolder=Juan | file=report.pdf"
     */
    static void log(String action, String details) {
        File logFile = new File(LOG);
        File logDir  = logFile.getParentFile();
        if (logDir != null && !logDir.exists()) logDir.mkdirs();

        String line = String.format("[%s] [%-20s] user=%-15s | %s",
                LocalDateTime.now().format(LOG_TS),
                action,
                Auth.currentUser(),
                details);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Could not write to log: " + e.getMessage());
        }
    }

    /** Legacy single-string overload — kept so old call sites compile. */
    static void writeLog(String raw) {
        File logFile = new File(LOG);
        File logDir  = logFile.getParentFile();
        if (logDir != null && !logDir.exists()) logDir.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            bw.write(raw);
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Could not write to log: " + e.getMessage());
        }
    }

    // ── Convenience log helpers (called from Main) ────────────────────────────

    static void logUpload(String category, String subFolder, String fileName) {
        log("UPLOAD", "category=" + category
                + " | subfolder=" + subFolder
                + " | file=" + fileName);
    }

    static void logDeleteFile(String category, String subFolder, String fileName) {
        log("DELETE-FILE", "category=" + category
                + " | subfolder=" + subFolder
                + " | file=" + fileName);
    }

    static void logDeleteSubFolder(String category, String subFolder) {
        log("DELETE-SUBFOLDER", "category=" + category
                + " | subfolder=" + subFolder
                + " | authorized=true");
    }

    static void logDeleteCategory(String category) {
        log("DELETE-CATEGORY", "category=" + category
                + " | authorized=true");
    }

    static void logRenameSubFolder(String category, String oldName, String newName) {
        log("RENAME-SUBFOLDER", "category=" + category
                + " | old=" + oldName
                + " | new=" + newName);
    }

    static void logRenameCategory(String oldName, String newName) {
        log("RENAME-CATEGORY", "old=" + oldName + " | new=" + newName);
    }

    static void logRenameFile(String byUser, String category, String subFolder,
                              String oldName, String newName) {
        log("RENAME-FILE", "by=" + byUser
                + " | category=" + category
                + " | subfolder=" + subFolder
                + " | old=" + oldName
                + " | new=" + newName);
    }

    // ── Fast path: O(1) single-file append (used by upload) ──────────────────

    /**
     * Appends exactly one entry to a subfolder's metadata .txt.
     * Does NOT call Fetcher.listFiles — only counts existing lines (O(n) read,
     * no directory enumeration).
     *
     * Metadata file: .data/<category>/<subFolder>/<subFolder>data.txt
     */
    static void appendToMetadata(String category, String subFolder, String fileName) {
        File dataFile = metadataFile(category, subFolder);
        File parentDir = dataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        int nextIndex = 0;
        if (dataFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(dataFile))) {
                while (br.readLine() != null) nextIndex++;
            } catch (IOException e) {
                System.err.println("[Writer] Could not count lines for "
                        + category + "/" + subFolder + ": " + e.getMessage());
            }
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataFile, true))) {
            bw.write(nextIndex + "|" + fileName + "|" + Main.dateFormat);
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException("[Writer] Could not append metadata for "
                    + category + "/" + subFolder + ": " + e.getMessage());
        }
    }

    // ── Manifest-driven full sync (external-change detection only) ────────────

    /**
     * Processes only the categories flagged true in the manifest.
     * For each such category, syncs every subfolder's metadata file.
     */
    static void updateAllChangedFolders() {
        List<String> needsUpdate = ManifestManager.getFoldersNeedingUpdate();
        for (String category : needsUpdate) {
            updateCategoryMetadata(category);
            ManifestManager.markFolderScanned(category);
        }
    }

    /**
     * Full sync for one category: iterates all its subfolders and
     * syncs each subfolder's metadata .txt against actual files on disk.
     * Expensive (calls listFiles) — only reached for external changes.
     */
    static void updateCategoryMetadata(String category) {
        File catDir = new File(DATA_ROOT + File.separator + category);
        File[] subDirs = catDir.listFiles(File::isDirectory);
        if (subDirs == null) return;

        for (File sub : subDirs) {
            syncSubFolderMetadata(category, sub.getName());
        }
    }

    /**
     * Bidirectional sync of one subfolder's metadata .txt.
     * Steps:
     *  1. Read existing .txt entries.
     *  2. List actual files on disk.
     *  3. Drop entries whose files no longer exist.
     *  4. Add entries for files not yet recorded.
     *  5. Rewrite only if something changed; reindex from 0.
     */
    private static void syncSubFolderMetadata(String category, String subFolder) {
        File dataFile = metadataFile(category, subFolder);
        File subDir   = new File(DATA_ROOT + File.separator + category
                + File.separator + subFolder);

        // Step 1: read existing metadata
        List<String[]> existing   = new ArrayList<>();
        Set<String>    knownNames = new HashSet<>();
        if (dataFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(dataFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        existing.add(parts);
                        knownNames.add(parts[1]);
                    }
                }
            } catch (Exception e) {
                System.err.println("[Writer] Error reading metadata: "
                        + category + "/" + subFolder);
            }
        }

        // Step 2: list actual files on disk
        String[]    diskArr   = Fetcher.getFolderData(category, subFolder);
        Set<String> diskFiles = new HashSet<>(Arrays.asList(diskArr));

        // Step 3: drop stale entries
        List<String[]> valid    = new ArrayList<>();
        boolean        hadStale = false;
        for (String[] e : existing) {
            if (diskFiles.contains(e[1])) { valid.add(e); }
            else                          { hadStale = true; }
        }

        // Step 4: new files not yet in metadata
        List<String[]> newEntries = new ArrayList<>();
        for (String f : diskArr) {
            if (!knownNames.contains(f))
                newEntries.add(new String[]{null, f, Main.dateFormat});
        }

        // Step 5: rewrite only if something changed
        if (!hadStale && newEntries.isEmpty()) return;

        List<String[]> final_ = new ArrayList<>(valid);
        final_.addAll(newEntries);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataFile, false))) {
            for (int i = 0; i < final_.size(); i++) {
                String[] e  = final_.get(i);
                String   ts = (e.length >= 3 && e[2] != null) ? e[2] : Main.dateFormat;
                bw.write(i + "|" + e[1] + "|" + ts);
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("[Writer] Could not write metadata for "
                    + category + "/" + subFolder + ": " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Canonical path for a subfolder's metadata file:
     * .data/<category>/<subFolder>/<subFolder>data.txt
     */
    static File metadataFile(String category, String subFolder) {
        return new File(DATA_ROOT
                + File.separator + category
                + File.separator + subFolder
                + File.separator + subFolder + "data.txt");
    }
    // ── ADD THESE METHODS to Writer.java ──────────────────────────────────────────

    static void logUserAdded(String byUser, String newUser) {
        log("USER-ADDED", "by=" + byUser + " | new_user=" + newUser);
    }

    static void logUserRemoved(String byUser, String removedUser) {
        log("USER-REMOVED", "by=" + byUser + " | removed_user=" + removedUser);
    }

    static void logCredentialsChanged(String user, String oldName, String newName) {
        log("CREDENTIALS-CHANGED", "user=" + user + " | old_name=" + oldName + " | new_name=" + newName);
    }

    static void logMoveFile(String byUser, String fromCategory, String fromSub,
                            String toCategory, String toSub, String fileName) {
        log("MOVE-FILE", "by=" + byUser
                + " | from=" + fromCategory + "/" + fromSub
                + " | to=" + toCategory + "/" + toSub
                + " | file=" + fileName);
    }

    static void logMoveSubFolder(String byUser, String fromCategory,
                                 String subFolder, String toCategory) {
        log("MOVE-SUBFOLDER", "by=" + byUser
                + " | subfolder=" + subFolder
                + " | from=" + fromCategory
                + " | to=" + toCategory);
    }

    /**
     * Remove a single file entry from a subfolder's metadata .txt.
     * Called when a file is moved OUT of a subfolder.
     */
    static void removeFromMetadata(String category, String subFolder, String fileName) {
        File dataFile = metadataFile(category, subFolder);
        if (!dataFile.exists()) return;

        java.util.List<String[]> lines = new java.util.ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2 && parts[1].equals(fileName)) continue; // skip this one
                lines.add(parts);
            }
        } catch (IOException e) {
            System.err.println("[Writer] Could not read metadata for removeFromMetadata: " + e.getMessage());
            return;
        }

        // Rewrite with re-indexed entries
        try (java.io.BufferedWriter bw = new BufferedWriter(new java.io.FileWriter(dataFile, false))) {
            for (int i = 0; i < lines.size(); i++) {
                String[] p = lines.get(i);
                String ts = (p.length >= 3 && p[2] != null) ? p[2] : Main.dateFormat;
                bw.write(i + "|" + p[1] + "|" + ts);
                bw.newLine();
            }
        } catch (java.io.IOException e) {
            System.err.println("[Writer] Could not write metadata after removeFromMetadata: " + e.getMessage());
        }
    }
}