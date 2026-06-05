import java.io.Serializable;

public class File implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String content;
    private final long createdAt;
    private long updatedAt;

    public File(String name, String content) {
        long now = System.currentTimeMillis();
        this.name = name;
        this.content = content == null ? "" : content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getContent() {
        return content;
    }

    public int getSize() {
        return content.length();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public File copy(String newName) {
        return new File(newName, content);
    }
}
