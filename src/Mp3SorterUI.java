import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Mp3SorterUI extends JFrame {

    private JTextField sourceField;
    private JTextField destinationField;

    private JTextField usbSizeField;
    private JButton setSizeButton;

    private AlbumSelectionPanel albumSelectionPanel;

    private JTextArea logArea;
    private JProgressBar progressBar;

    private long maxSizeBytes = 256L * 1024 * 1024;

    public Mp3SorterUI() {
        super("MP3 Sorter by Track #");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Panel: inputs + buttons
        JPanel inputPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Source folder input + browse button
        sourceField = new JTextField();
        sourceField.setEditable(false);
        JButton browseSource = new JButton("Browse Source");
        browseSource.addActionListener(e -> chooseSourceFolder());
        JPanel sourceRow = new JPanel(new BorderLayout(5,5));
        sourceRow.add(new JLabel("Source Folder:"), BorderLayout.WEST);
        sourceRow.add(sourceField, BorderLayout.CENTER);
        sourceRow.add(browseSource, BorderLayout.EAST);

        // Destination folder input + browse button
        destinationField = new JTextField();
        destinationField.setEditable(false);
        JButton browseDest = new JButton("Browse Destination");
        browseDest.addActionListener(e -> chooseFolder(destinationField));
        JPanel destRow = new JPanel(new BorderLayout(5,5));
        destRow.add(new JLabel("Destination Folder:"), BorderLayout.WEST);
        destRow.add(destinationField, BorderLayout.CENTER);
        destRow.add(browseDest, BorderLayout.EAST);

        // USB size limit input + set button
        usbSizeField = new JTextField("256", 6);
        setSizeButton = new JButton("Set Size");
        setSizeButton.addActionListener(e -> setUsbSizeLimit());
        JPanel usbRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        usbRow.add(new JLabel("USB Size Limit (MB):"));
        usbRow.add(usbSizeField);
        usbRow.add(setSizeButton);

        // Start button
        JButton startButton = new JButton("Start Transfer");
        startButton.addActionListener(this::startSorting);

        inputPanel.add(sourceRow);
        inputPanel.add(destRow);
        inputPanel.add(usbRow);
        inputPanel.add(startButton);

        add(inputPanel, BorderLayout.NORTH);

        // Album selection panel
        albumSelectionPanel = new AlbumSelectionPanel(maxSizeBytes);
        JScrollPane albumScroll = new JScrollPane(albumSelectionPanel);
        albumScroll.setBorder(BorderFactory.createTitledBorder("Select Albums to Include"));
        albumScroll.setPreferredSize(new Dimension(300, 200));
        add(albumScroll, BorderLayout.WEST);

        // Log area + progress bar
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(logScroll, BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.CENTER);
    }

    private void setUsbSizeLimit() {
        String text = usbSizeField.getText().trim();
        try {
            long mb = Long.parseLong(text);
            if (mb <= 0) throw new NumberFormatException();
            maxSizeBytes = mb * 1024L * 1024L;
            albumSelectionPanel.setMaxSizeBytes(maxSizeBytes);
            log("USB size limit set to " + mb + " MB");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a positive valid number for USB size limit.",
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

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

    private void chooseSourceFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            sourceField.setText(selected.getAbsolutePath());
            sourceField.setToolTipText(selected.getAbsolutePath());
            albumSelectionPanel.populate(selected);
        }
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

        List<File> selectedAlbums = albumSelectionPanel.getSelectedAlbums();

        if (!albumSelectionPanel.hasAlbums() && selectedAlbums.isEmpty()) {
            log("❗ No albums selected or no albums found. Nothing to do.");
            return;
        }

        File source = new File(sourcePath);
        File dest = new File(destPath);
        if (!source.exists() || !source.isDirectory()) {
            log("❌ Invalid source folder.");
            return;
        }
        if (!dest.exists() && !dest.mkdirs()) {
            log("❌ Could not create destination folder.");
            return;
        }

        new Thread(() -> {
            try {
                progressBar.setValue(0);
                progressBar.setString("Starting...");

                if (!albumSelectionPanel.hasAlbums()) {
                    log("ℹ️ No subfolders detected; processing root files.");
                    Mp3Processor.processSingleFolder(source, dest, this::log, this::updateProgress);
                } else {
                    boolean isArtistFolder = selectedAlbums.stream()
                            .anyMatch(file -> file.isDirectory() && !containsMp3Files(file));

                    if (isArtistFolder) {
                        Mp3Processor.processSelectedAlbumsWithSubfolders(selectedAlbums, dest, this::log, this::updateProgress);
                    } else {
                        Mp3Processor.processSelectedAlbums(selectedAlbums, dest, this::log, this::updateProgress);
                    }
                }
                log("✅ Done.");
                progressBar.setValue(100);
                progressBar.setString("Done");
            } catch (Exception ex) {
                ex.printStackTrace();
                log("❌ Error: " + ex.getMessage());
                progressBar.setString("Error");
            }
        }).start();
    }

    private boolean containsMp3Files(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".mp3")) {
                return true;
            }
        }
        return false;
    }


    private void updateProgress(int percent, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressBar.setString(message);
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private List<File> findAlbums(File musicRoot) {
        List<File> albums = new ArrayList<>();
        if (musicRoot == null || !musicRoot.isDirectory()) return albums;

        File[] artistDirs = musicRoot.listFiles(File::isDirectory);
        if (artistDirs == null) return albums;

        for (File artist : artistDirs) {
            File[] albumDirs = artist.listFiles(File::isDirectory);
            if (albumDirs == null) continue;

            for (File album : albumDirs) {
                albums.add(album);
            }
        }

        return albums;
    }





    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Mp3SorterUI ui = new Mp3SorterUI();
            ui.setVisible(true);
        });
    }
}
