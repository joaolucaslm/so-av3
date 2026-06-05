import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Directory implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private final Map<String, Directory> directories;
    private final Map<String, File> files;

    public Directory(String name) {
        this.name = name;
        this.directories = new TreeMap<String, Directory>();
        this.files = new TreeMap<String, File>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Directory> getDirectories() {
        return directories;
    }

    public Map<String, File> getFiles() {
        return files;
    }

    public boolean hasChild(String childName) {
        return directories.containsKey(childName) || files.containsKey(childName);
    }

    public boolean isEmpty() {
        return directories.isEmpty() && files.isEmpty();
    }

    public List<String> listEntries() {
        List<String> entries = new ArrayList<String>();

        for (Directory directory : directories.values()) {
            entries.add("[DIR]  " + directory.getName());
        }

        for (File file : files.values()) {
            entries.add("[FILE] " + file.getName() + " (" + file.getSize() + " bytes)");
        }

        return entries;
    }
}
