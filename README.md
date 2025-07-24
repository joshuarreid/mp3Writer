

# Mp3Writer

**Mp3Sorter** is a Java desktop application that helps organize and transfer your MP3 files. It copies songs from a source folder (organized by albums/subfolders) to a destination folder, writing the files in **track number order** to support MP3 players that ignore metadata and play files in the order they were written to disk.

Built with **Java Swing**, the app includes a simple graphical interface for selecting source and destination folders, as well as filtering which subfolders (albums) to include.

## Preview

![Screenshot of Mp3Sorter](https://raw.githubusercontent.com/joshuarreid/mp3Writer/main/img_1.png)

---

## Features

- ✅ Java Swing-based graphical interface  
- ✅ Browse and select a source folder of albums  
- ✅ Checkboxes to choose which album folders to include  
- ✅ Copies MP3 files sorted by track number  
- ✅ Ensures the order of writing matches track sequencing  
- ✅ Compatible with USB-based MP3 players

---

## How It Works

1. You select the source directory containing album folders.
2. The app reads and displays all subfolders.
3. You choose which folders to include using checkboxes.
4. You select a destination folder.
5. The app copies the files, ordered by their track number tags.

---

## Requirements

- Java 17+
- JavaFX (optional if using custom UI extensions)
- macOS or Windows (tested with jpackage for macOS)

---

## Building the App

To generate a `.jar` file:

1. Open the project in IntelliJ.
2. Go to **File > Project Structure > Artifacts**.
3. Click the `+` sign and select **JAR > From modules with dependencies**.
4. Set the **main class**.
5. Build the artifact from **Build > Build Artifacts > Build**.

---

## Packaging as a macOS App

Use `jpackage`:

```bash
jpackage \
  --type app-image \
  --name Mp3Sorter \
  --input . \
  --main-jar mp3_writer.jar \
  --icon app.icns \
  --dest out
