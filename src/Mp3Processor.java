import com.mpatric.mp3agic.Mp3File;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Mp3Processor {

    public static void processSelectedAlbums(List<File> albums, File destinationRoot,
                                             Consumer<String> log,
                                             BiConsumer<Integer, String> progressUpdater) throws Exception {
        int totalAlbums = albums.size();
        int processed = 0;

        for (File album : albums) {
            processSingleFolder(album, new File(destinationRoot, album.getName()), log, progressUpdater);
            processed++;
            int progress = (int) (((double) processed / totalAlbums) * 100);
            progressUpdater.accept(progress, "Processing albums: " + progress + "%");
        }
    }

    public static void processSelectedAlbumsWithSubfolders(List<File> artistFolders, File destinationRoot,
                                             Consumer<String> log,
                                             BiConsumer<Integer, String> progressUpdater) throws Exception {
        List<File> albumFolders = new ArrayList<>();

        for (File artistFolder : artistFolders) {
            // Recursively find album folders containing mp3s
            findAlbumFolders(artistFolder, albumFolders);
        }

        int totalAlbums = albumFolders.size();
        int processed = 0;

        for (File album : albumFolders) {
            File destFolder = new File(destinationRoot, album.getName());
            processSingleFolder(album, destFolder, log, progressUpdater);
            processed++;
            int progress = (int) (((double) processed / totalAlbums) * 100);
            progressUpdater.accept(progress, "Processing albums: " + progress + "%");
        }
    }

    private static void findAlbumFolders(File folder, List<File> albumFolders) {
        File[] subFiles = folder.listFiles();

        if (subFiles == null) return;

        boolean containsMp3 = false;
        for (File f : subFiles) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".mp3")) {
                containsMp3 = true;
                break;
            }
        }

        if (containsMp3) {
            albumFolders.add(folder);
        } else {
            for (File f : subFiles) {
                if (f.isDirectory()) {
                    findAlbumFolders(f, albumFolders);
                }
            }
        }
    }



    public static void processSingleFolder(File sourceFolder, File destinationFolder,
                                           Consumer<String> log,
                                           BiConsumer<Integer, String> progressUpdater) throws Exception {
        File[] mp3Files = sourceFolder.listFiles(f -> f.getName().toLowerCase().endsWith(".mp3"));
        if (mp3Files == null || mp3Files.length == 0) {
            log.accept("(No MP3 files in: " + sourceFolder.getName() + ")");
            return;
        }

        List<Track> tracks = new ArrayList<>();
        for (File mp3 : mp3Files) {
            try {
                Mp3File mp3file = new Mp3File(mp3);
                int trackNumber = getTrackNumber(mp3file);
                tracks.add(new Track(trackNumber, mp3));
            } catch (Exception e) {
                log.accept("⚠️ Skipping: " + mp3.getName() + " - " + e.getMessage());
            }
        }

        tracks.sort(Comparator.comparingInt(t -> t.trackNumber));

        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }

        int totalTracks = tracks.size();
        int copied = 0;
        for (Track track : tracks) {
            String newName = track.file.getName();
            File destFile = new File(destinationFolder, newName);
            Files.copy(track.file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.accept("✅ Copied: " + destinationFolder.getName() + "/" + newName);

            copied++;
            int progress = (int) (((double) copied / totalTracks) * 100);
            progressUpdater.accept(progress, "Copying tracks: " + progress + "%");
        }
    }

    public static long getFolderSize(File folder) {
        long size = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                } else if (f.isDirectory()) {
                    size += getFolderSize(f);
                }
            }
        }
        return size;
    }

    private static int getTrackNumber(Mp3File mp3) {
        if (mp3.hasId3v2Tag()) {
            String track = mp3.getId3v2Tag().getTrack();
            return parseTrackNumber(track);
        } else if (mp3.hasId3v1Tag()) {
            String track = mp3.getId3v1Tag().getTrack();
            return parseTrackNumber(track);
        }
        return 0;
    }

    private static int parseTrackNumber(String track) {
        if (track == null) return 0;
        String[] parts = track.split("/");
        String num = parts[0].replaceAll("\\D+", "");
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class Track {
        int trackNumber;
        File file;

        Track(int trackNumber, File file) {
            this.trackNumber = trackNumber;
            this.file = file;
        }
    }
}
