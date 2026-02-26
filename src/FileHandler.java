import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileHandler {
    private static final String MAIN_LOCATION = System.getProperty("user.home")
            + File.separator + ".SFADSMS";

    static boolean verify(File newFile, File oldFile) throws IOException {
        return Files.exists(newFile.toPath())
                && Files.size(newFile.toPath()) == Files.size(oldFile.toPath());
    }

    /**
     * Moves a file into  .data / category / subFolder / filename.ext
     * <p>
     * jComboBox    → category (top-level sidebar folder)
     * subFolderBox → sub-folder name (the "record" level — what gets renamed)
     * field        → display name for the file
     * <p>
     * Returns the destination File on success, null on failure/cancel.
     */
    static File moveFiles(File selectedFile,
                          JComboBox<String> categoryBox,
                          JComboBox<String> subFolderBox,
                          JTextField field,
                          JDialog dialog) throws IOException {
        String category = resolveCombo(categoryBox);
        String subFolder = resolveCombo(subFolderBox);

        if (category == null) {
            JOptionPane.showMessageDialog(dialog,
                    "Please enter or select a category folder.",
                    "Missing Category", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (subFolder == null) {
            JOptionPane.showMessageDialog(dialog,
                    "Please enter or select a sub-folder name.",
                    "Missing Sub-folder", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String extension = selectedFile.getName()
                .substring(selectedFile.getName().lastIndexOf("."));
        File destinationFolder = new File(MAIN_LOCATION + File.separator + ".data"
                + File.separator + category + File.separator + subFolder);
        if (!destinationFolder.exists()) destinationFolder.mkdirs();

        File newFile = new File(destinationFolder, field.getText().trim() + extension);

        if (newFile.exists()) {
            JOptionPane.showMessageDialog(dialog,
                    "A file named '" + field.getText().trim() + "' already exists here.");
            return null;
        }

        try {
            Files.copy(selectedFile.toPath(), newFile.toPath());
            if (verify(newFile, selectedFile)) {
                Files.delete(selectedFile.toPath());
                Writer.writeLog("[OPERATION] Moved " + selectedFile.getName()
                        + " to " + newFile.getAbsolutePath());
                dialog.dispose();
                return newFile;
            } else {
                Files.deleteIfExists(newFile.toPath());
                Writer.writeLog("[ERROR] Verification failed for " + selectedFile.getName());
                JOptionPane.showMessageDialog(dialog,
                        "File copy verification failed. Please try again.");
                return null;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, "Copy failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the live typed text from an editable JComboBox editor first,
     * falls back to the selected model item.
     * Returns null if both are blank — caller must show a warning.
     */
    static String resolveCombo(JComboBox<String> box) {
        Object editor = box.getEditor().getItem();
        if (editor != null && !editor.toString().trim().isEmpty())
            return editor.toString().trim();
        Object selected = box.getSelectedItem();
        if (selected != null && !selected.toString().trim().isEmpty())
            return selected.toString().trim();
        return null;
    }
}