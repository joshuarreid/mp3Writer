import com.mpatric.mp3agic.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * MP3 Sorter Swing UI
 * -------------------
 * After selecting a source folder, the UI scans for immediate child subfolders (albums)
 * and displays them in a scrollable checklist. You can check/uncheck which albums to include
 * in the copy/sort operation. All are selected by default.
 *
 * MP3s inside each selected album are read for track # (ID3v2 -> ID3v1 fallback), sorted by track,
 * and copied to the destination in write order, renamed as "NN - originalFileName.mp3".
 *
 * External dependency: mp3agic (https://github.com/mpatric/mp3agic)
 * Maven: com.mpatric:mp3agic:0.9.1
 */
public class Mp3SorterUI extends JFrame {

    // Path fields (read-only so user can copy them)
    private JTextField sourceField;
    private JTextField destinationField;

    // Log output
    private JTextArea logArea;

    // Album selection components
    private JPanel albumPanel;                    // Holds the checkboxes
    private JScrollPane albumScroll;              // Scroll container for album panel
    private final java.util.List<JCheckBox> albumChecks = new ArrayList<>();
    private final Map<JCheckBox, File> albumMap = new HashMap<>();

    public Mp3SorterUI() {
        setTitle("MP3 Sorter by Track #");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Top input controls ----------------------------------------------------------
        JPanel inputPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        sourceField = new JTextField();
        sourceField.setEditable(false);
        destinationField = new JTextField();
        destinationField.setEditable(false);

        JButton browseSource = new JButton("Browse Source");
        browseSource.addActionListener(e -> chooseSourceFolder());

        JButton browseDest = new JButton("Browse Destination");
        browseDest.addActionListener(e -> chooseFolder(destinationField));

        JPanel sourceRow = new JPanel(new BorderLayout(5, 5));
        sourceRow.add(new JLabel("Source Folder:"), BorderLayout.WEST);
        sourceRow.add(sourceField, BorderLayout.CENTER);
        sourceRow.add(browseSource, BorderLayout.EAST);

        JPanel destRow = new JPanel(new BorderLayout(5, 5));
        destRow.add(new JLabel("Destination Folder:"), BorderLayout.WEST);
        destRow.add(destinationField, BorderLayout.CENTER);
        destRow.add(browseDest, BorderLayout.EAST);

        JButton startButton = new JButton("Start Transfer");
        startButton.addActionListener(this::startSorting);

        inputPanel.add(sourceRow);
        inputPanel.add(destRow);
        inputPanel.add(startButton);

        add(inputPanel, BorderLayout.NORTH);

        // --- Album selection area -------------------------------------------------------
        albumPanel = new JPanel();
        albumPanel.setLayout(new BoxLayout(albumPanel, BoxLayout.Y_AXIS));
        albumPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        albumScroll = new JScrollPane(albumPanel);
        albumScroll.setBorder(BorderFactory.createTitledBorder("Select Albums to Include"));
        albumScroll.setPreferredSize(new Dimension(300, 200));

        // --- Log area -------------------------------------------------------------------
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));

        // Use a vertical split pane: top = albums, bottom = log
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, albumScroll, logScroll);
        split.setResizeWeight(0.4); // allocate 40% to album list initially
        add(split, BorderLayout.CENTER);
    }

    /**
     * Choose a generic folder (used for destination).
     */
    private void chooseFolder(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            targetField.setText(selected.getAbsolutePath());
            targetField.setToolTipText(selected.getAbsolutePath());
        }
    }

    /**
     * Choose the source folder, then populate the album checkbox list.
     */
    private void chooseSourceFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            sourceField.setText(selected.getAbsolutePath());
            sourceField.setToolTipText(selected.getAbsolutePath());
            populateAlbumList(selected);
        }
    }

    /**
     * Scan the source directory for immediate child subfolders and create checkboxes for each.
     * All checkboxes are selected by default.
     */
    private void populateAlbumList(File sourceDir) {
        albumPanel.removeAll();
        albumChecks.clear();
        albumMap.clear();

        File[] subfolders = sourceDir.listFiles(File::isDirectory);
        if (subfolders == null || subfolders.length == 0) {
            JLabel none = new JLabel("(No subfolders found)");
            albumPanel.add(none);
        } else {
            for (File sub : Objects.requireNonNull(subfolders)) {
                JCheckBox cb = new JCheckBox(sub.getName(), true);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                albumChecks.add(cb);
                albumMap.put(cb, sub);
                albumPanel.add(cb);
            }
        }
        albumPanel.revalidate();
        albumPanel.repaint();
    }

    private void startSorting(ActionEvent e) {
        String sourcePath = sourceField.getText().trim();
        String destPath = destinationField.getText().trim();

        if (sourcePath.isEmpty()) {
            log("❗ Please select a source folder.");
            return;
        }
        if (destPath.isEmpty()) {
            log("❗ Please select a destination folder.");
            return;
        }

        // Gather selected albums
        List<File> selectedAlbums = new ArrayList<>();
        for (JCheckBox cb : albumChecks) {
            if (cb.isSelected()) {
                File f = albumMap.get(cb);
                if (f != null) selectedAlbums.add(f);
            }
        }

        if (albumChecks.size() > 0 && selectedAlbums.isEmpty()) {
            log("❗ No albums selected. Nothing to do.");
            return;
        }

        File source = new File(sourcePath);
        File dest = new File(destPath);
        if (!source.exists() || !source.isDirectory()) {
            log("❌ Invalid source folder.");
            return;
        }
        if (!dest.exists()) {
            boolean ok = dest.mkdirs();
            if (!ok) {
                log("❌ Could not create destination folder.");
                return;
            }
        }

        new Thread(() -> {
            try {
                if (albumChecks.isEmpty()) {
                    // No subfolders - process the root itself
                    log("ℹ️ No subfolders detected; processing root files.");
                    processSingleFolder(source, dest);
                } else {
                    processSelectedAlbums(selectedAlbums, dest);
                }
                log("✅ Done.");
            } catch (Exception ex) {
                ex.printStackTrace();
                log("❌ Error: " + ex.getMessage());
            }
        }).start();
    }

    /**
     * Process only the user-selected album folders.
     */
    private void processSelectedAlbums(List<File> albums, File destinationRoot) throws Exception {
        for (File album : albums) {
            processSingleFolder(album, new File(destinationRoot, album.getName()));
        }
    }

    /**
     * Process a single folder (reads MP3s, sorts by track #, copies to destinationFolder).
     * destinationFolder may be root or an album subdir.
     */
    private void processSingleFolder(File sourceFolder, File destinationFolder) throws Exception {
        File[] mp3Files = sourceFolder.listFiles(f -> f.getName().toLowerCase().endsWith(".mp3"));
        if (mp3Files == null || mp3Files.length == 0) {
            log("(No MP3 files in: " + sourceFolder.getName() + ")");
            return;
        }

        List<Track> tracks = new ArrayList<>();
        for (File mp3 : mp3Files) {
            try {
                Mp3File mp3file = new Mp3File(mp3);
                int trackNumber = getTrackNumber(mp3file);
                tracks.add(new Track(trackNumber, mp3));
            } catch (Exception e) {
                log("⚠️ Skipping: " + mp3.getName() + " - " + e.getMessage());
            }
        }

        tracks.sort(Comparator.comparingInt(t -> t.trackNumber));

        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }

        for (Track track : tracks) {
            String newName = track.file.getName();
            File destFile = new File(destinationFolder, newName);
            Files.copy(track.file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log("✅ Copied: " + destinationFolder.getName() + "/" + newName);
        }
    }

    private int getTrackNumber(Mp3File mp3) {
        if (mp3.hasId3v2Tag()) {
            String track = mp3.getId3v2Tag().getTrack();
            return parseTrackNumber(track);
        } else if (mp3.hasId3v1Tag()) {
            String track = mp3.getId3v1Tag().getTrack();
            return parseTrackNumber(track);
        }
        return 0;
    }

    private int parseTrackNumber(String track) {
        if (track == null) return 0;
        String[] parts = track.split("/");
        String num = parts[0].replaceAll("\\D+", "");
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static class Track {
        int trackNumber;
        File file;
        public Track(int trackNumber, File file) {
            this.trackNumber = trackNumber;
            this.file = file;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Mp3SorterUI app = new Mp3SorterUI();
            app.setVisible(true);
        });
    }
}
