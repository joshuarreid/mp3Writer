import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.File;
import java.util.*;
import java.util.List;

public class AlbumSelectionPanel extends JPanel {
    private final List<JCheckBox> albumChecks = new ArrayList<>();
    private final Map<JCheckBox, File> albumMap = new HashMap<>();
    private final Map<JCheckBox, Long> albumSizes = new HashMap<>();

    private long maxSizeBytes;
    private long currentSelectedSize = 0;

    private final JLabel sizeLabel = new JLabel("Total selected: 0.00 MB");

    public AlbumSelectionPanel(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10,10,10,10));
        add(sizeLabel);
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
        currentSelectedSize = 0;
        // Uncheck all when max size changes
        for (JCheckBox cb : albumChecks) {
            cb.setSelected(false);
        }
        updateSizeLabel();
    }

    public void populate(File musicFolder) {
        removeAll();
        albumChecks.clear();
        albumMap.clear();
        albumSizes.clear();
        currentSelectedSize = 0;

        File[] subfolders = musicFolder.listFiles(File::isDirectory);
        if (subfolders == null || subfolders.length == 0) {
            add(new JLabel("No albums found."));
        } else {
            for (File albumFolder : subfolders) {
                long size = Mp3Processor.getFolderSize(albumFolder);
                String label = String.format("%s (%.2f MB)", albumFolder.getName(), size / (1024.0 * 1024.0));
                JCheckBox cb = new JCheckBox(label);

                albumChecks.add(cb);
                albumMap.put(cb, albumFolder);
                albumSizes.put(cb, size);

                cb.addActionListener(e -> {
                    boolean checked = cb.isSelected();
                    long delta = checked ? size : -size;
                    long newTotal = currentSelectedSize + delta;

                    if (newTotal > maxSizeBytes) {
                        cb.setSelected(false);
                        JOptionPane.showMessageDialog(this,
                                "Selection exceeds USB size limit.",
                                "Limit Exceeded",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        currentSelectedSize = newTotal;
                        updateSizeLabel();
                    }
                });

                add(cb);
            }
        }
        add(Box.createVerticalStrut(10));
        add(sizeLabel);
        revalidate();
        repaint();
        updateSizeLabel();
    }

    private void updateSizeLabel() {
        sizeLabel.setText(String.format("Total selected: %.2f MB / Max: %.2f MB",
                currentSelectedSize / (1024.0 * 1024.0),
                maxSizeBytes / (1024.0 * 1024.0)));
    }

    public List<File> getSelectedAlbums() {
        List<File> selected = new ArrayList<>();
        for (JCheckBox cb : albumChecks) {
            if (cb.isSelected()) {
                File f = albumMap.get(cb);
                if (f != null) selected.add(f);
            }
        }
        return selected;
    }

    public boolean hasAlbums() {
        return !albumChecks.isEmpty();
    }
}
