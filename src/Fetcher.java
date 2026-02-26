import java.io.File;
import java.util.Arrays;

public class Fetcher {

    private static final String DATA_DIR = System.getProperty("user.home")
            + File.separator + ".SFADSMS" + File.separator + ".data";

    /** Top-level category folders shown in the sidebar. */
    static String[] getFolderName() {
        File file = new File(DATA_DIR);
        String[] collections = file.list();
        if (collections == null) return new String[0];

        int valid = 0;
        for (String s : collections) if (!s.contains(".")) valid++;

        String[] containers = new String[valid];
        int index = 0;
        for (String s : collections) if (!s.contains(".")) containers[index++] = s;
        return containers;
    }

    /**
     * Sub-folders inside a top-level category folder.
     * These are the "records" shown in Level 1 of the table —
     * the things that get renamed via the Edit button.
     *
     * e.g. .data/Grade7/JuanDelaCruz  ← returned as "JuanDelaCruz"
     */
    static String[] getSubFolders(String parentFolder) {
        File dir = new File(DATA_DIR + File.separator + parentFolder);
        File[] items = dir.listFiles(File::isDirectory);
        if (items == null) return new String[0];
        String[] names = new String[items.length];
        for (int i = 0; i < items.length; i++) names[i] = items[i].getName();
        Arrays.sort(names);
        return names;
    }

    /**
     * Actual data files (pdf, png, doc, etc.) inside a sub-folder.
     * Shown in Level 2 of the table.
     *
     * e.g. .data/Grade7/JuanDelaCruz/report.pdf
     */
    static String[] getFolderData(String parentFolder, String subFolder) {
        File dir = new File(DATA_DIR + File.separator + parentFolder
                + File.separator + subFolder);
        String[] files = dir.list();
        if (files == null) return new String[0];

        int valid = 0;
        for (String f : files)
            if (f.contains(".") && !f.endsWith(".txt")) valid++;

        String[] result = new String[valid];
        int index = 0;
        for (String f : files)
            if (f.contains(".") && !f.endsWith(".txt")) result[index++] = f;
        return result;
    }

    /**
     * Legacy overload — kept so Writer.updateFolderMetadata still compiles.
     * Routes through the two-arg version by splitting on the first separator.
     * Direct callers should prefer getFolderData(parent, sub).
     */
    static String[] getFolderData(String folderName) {
        File dir = new File(DATA_DIR + File.separator + folderName);
        String[] files = dir.list();
        if (files == null) return new String[0];

        int valid = 0;
        for (String f : files)
            if (f.contains(".") && !f.endsWith(".txt")) valid++;

        String[] result = new String[valid];
        int index = 0;
        for (String f : files)
            if (f.contains(".") && !f.endsWith(".txt")) result[index++] = f;
        return result;
    }
}