import java.io.*;
import java.util.*;

/**
 * Manages the manifest file: ~/.SFADSMS/manifest.txt
 *
 * The manifest tracks TOP-LEVEL category folders (e.g. Grade7, Grade8).
 * true  = category has changed and needs a metadata sync
 * false = category is up-to-date
 *
 * File layout unchanged:
 *   categoryName|true
 *   categoryName|false
 *
 * Count detection is now recursive — it sums files across all subfolders
 * inside a category, matching the two-level structure:
 *   .data/<category>/<subFolder>/<files>
 */
public class ManifestManager {

    private static final String MANIFEST = System.getProperty("user.home")
            + File.separator + ".SFADSMS"
            + File.separator + "manifest.txt";

    private static final String DATA_DIR = System.getProperty("user.home")
            + File.separator + ".SFADSMS"
            + File.separator + ".data";

    // In-memory cache — disk read happens only once at startup
    private static Map<String, Boolean> cache = null;

    private static Map<String, Boolean> getCache() {
        if (cache == null) cache = loadFromDisk();
        return cache;
    }

    private static Map<String, Boolean> loadFromDisk() {
        Map<String, Boolean> map  = new HashMap<>();
        File                 file = new File(MANIFEST);
        if (!file.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) map.put(parts[0], parts[1].equals("true"));
            }
        } catch (IOException e) {
            System.err.println("[Manifest] Error reading from disk: " + e.getMessage());
        }
        return map;
    }

    private static void flushToDisk() {
        if (cache == null) return;
        File parent = new File(MANIFEST).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(MANIFEST))) {
            for (Map.Entry<String, Boolean> e : cache.entrySet()) {
                bw.write(e.getKey() + "|" + e.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("[Manifest] Error writing to disk: " + e.getMessage());
        }
    }

    // ── Folder discovery ──────────────────────────────────────────────────────

    /** Top-level category directories on disk. */
    private static Set<String> getCategoriesOnDisk() {
        Set<String> folders = new HashSet<>();
        File[] items = new File(DATA_DIR).listFiles(File::isDirectory);
        if (items != null) for (File f : items) folders.add(f.getName());
        return folders;
    }

    // ── Count helpers (recursive over subfolders) ─────────────────────────────

    /**
     * Total data files across all subfolders of a category.
     * Excludes each subfolder's own <subFolder>data.txt metadata file.
     *
     * .data/<category>/<subFolder1>/file1.pdf  ← counted
     * .data/<category>/<subFolder1>/subFolder1data.txt ← NOT counted
     */
    private static int countFilesOnDisk(String category) {
        File catDir = new File(DATA_DIR + File.separator + category);
        if (!catDir.exists()) return 0;
        File[] subDirs = catDir.listFiles(File::isDirectory);
        if (subDirs == null) return 0;

        int total = 0;
        for (File sub : subDirs) {
            String metaName = sub.getName() + "data.txt";
            File[] files = sub.listFiles(f -> f.isFile() && !f.getName().equals(metaName));
            if (files != null) total += files.length;
        }
        return total;
    }

    /**
     * Total metadata entries across all subfolders of a category.
     * Reads each <subFolder>data.txt and counts lines.
     */
    private static int countEntriesInTxt(String category) {
        File catDir = new File(DATA_DIR + File.separator + category);
        if (!catDir.exists()) return 0;
        File[] subDirs = catDir.listFiles(File::isDirectory);
        if (subDirs == null) return 0;

        int total = 0;
        for (File sub : subDirs) {
            File txt = new File(sub, sub.getName() + "data.txt");
            if (!txt.exists()) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(txt))) {
                while (br.readLine() != null) total++;
            } catch (IOException e) { /* treat as 0 */ }
        }
        return total;
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    /**
     * Reconciles cache with disk reality:
     * 1. Adds newly created categories (marked true = needs scan).
     * 2. Removes categories deleted from disk.
     * 3. For categories currently marked false, compares recursive file count
     *    on disk vs total metadata entries — drift means external change.
     *
     * Only flushes to disk if something actually changed.
     */
    static void syncManifest() {
        Map<String, Boolean> manifest      = getCache();
        Set<String>          diskCategories = getCategoriesOnDisk();
        boolean              changed       = false;

        // Add new categories
        for (String cat : diskCategories) {
            if (!manifest.containsKey(cat)) {
                manifest.put(cat, true);
                changed = true;
            }
        }

        // Remove deleted categories
        Iterator<String> it = manifest.keySet().iterator();
        while (it.hasNext()) {
            if (!diskCategories.contains(it.next())) { it.remove(); changed = true; }
        }

        // Detect external file additions/removals via count comparison
        for (String cat : diskCategories) {
            if (Boolean.FALSE.equals(manifest.get(cat))) {
                int onDisk = countFilesOnDisk(cat);
                int inTxt  = countEntriesInTxt(cat);
                if (onDisk != inTxt) {
                    manifest.put(cat, true);   // drift → needs rescan
                    changed = true;
                }
            }
        }

        if (changed) flushToDisk();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Mark a category as needing a metadata sync. */
    static void markFolderChanged(String category) {
        if (category == null || category.isBlank()) return;
        Map<String, Boolean> manifest = getCache();
        if (!Boolean.TRUE.equals(manifest.get(category))) {
            manifest.put(category, true);
            flushToDisk();
        }
    }

    /** Mark a category as up-to-date (no sync needed). */
    static void markFolderScanned(String category) {
        if (category == null || category.isBlank()) return;
        Map<String, Boolean> manifest = getCache();
        if (!Boolean.FALSE.equals(manifest.get(category))) {
            manifest.put(category, false);
            flushToDisk();
        }
    }

    /**
     * Returns categories that need a metadata sync.
     * Calls syncManifest() first to catch external changes.
     */
    static List<String> getFoldersNeedingUpdate() {
        syncManifest();
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : getCache().entrySet())
            if (e.getValue()) list.add(e.getKey());
        return list;
    }

    /** Rename a category in the manifest cache (call after renaming the directory). */
    static void renameCategory(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        Map<String, Boolean> manifest = getCache();
        Boolean val = manifest.remove(oldName);
        manifest.put(newName, val != null ? val : false);
        flushToDisk();
    }

    /** Remove a category from the manifest cache (call after deleting the directory). */
    static void removeCategory(String category) {
        if (category == null) return;
        if (getCache().remove(category) != null) flushToDisk();
    }

    /** Read-only view — no disk hit. */
    static Map<String, Boolean> readManifest() {
        return Collections.unmodifiableMap(getCache());
    }

    /** Force full cache reload from disk. */
    static void invalidateCache() {
        cache = null;
    }
}