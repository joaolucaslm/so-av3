import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class FileSystemSimulator {
    private static final String OP_CREATE_FILE = "CREATE_FILE";
    private static final String OP_COPY_FILE = "COPY_FILE";
    private static final String OP_DELETE_FILE = "DELETE_FILE";
    private static final String OP_RENAME_FILE = "RENAME_FILE";
    private static final String OP_CREATE_DIRECTORY = "CREATE_DIRECTORY";
    private static final String OP_DELETE_DIRECTORY = "DELETE_DIRECTORY";
    private static final String OP_RENAME_DIRECTORY = "RENAME_DIRECTORY";

    private final Path imagePath;
    private final Journal journal;
    private Directory root;
    private long lastAppliedTransactionId;

    public FileSystemSimulator(String imagePath, String journalPath) throws Exception {
        this(Paths.get(imagePath), Paths.get(journalPath));
    }

    public FileSystemSimulator(Path imagePath, Path journalPath) throws Exception {
        this.imagePath = imagePath;
        this.journal = new Journal(journalPath);
        loadSnapshot();
        recoverFromJournal();
    }

    public void createFile(String path, String content) throws IOException {
        executeJournaled(OP_CREATE_FILE, Arrays.asList(path, content == null ? "" : content));
    }

    public void copyFile(String sourcePath, String destinationPath) throws IOException {
        executeJournaled(OP_COPY_FILE, Arrays.asList(sourcePath, destinationPath));
    }

    public void deleteFile(String path) throws IOException {
        executeJournaled(OP_DELETE_FILE, Arrays.asList(path));
    }

    public void renameFile(String path, String newName) throws IOException {
        executeJournaled(OP_RENAME_FILE, Arrays.asList(path, newName));
    }

    public void createDirectory(String path) throws IOException {
        executeJournaled(OP_CREATE_DIRECTORY, Arrays.asList(path));
    }

    public void deleteDirectory(String path) throws IOException {
        executeJournaled(OP_DELETE_DIRECTORY, Arrays.asList(path));
    }

    public void renameDirectory(String path, String newName) throws IOException {
        executeJournaled(OP_RENAME_DIRECTORY, Arrays.asList(path, newName));
    }

    public List<String> listDirectory(String path) {
        Directory directory = getDirectory(normalizePath(path));
        return directory.listEntries();
    }

    public String tree(String path) {
        String normalizedPath = normalizePath(path);
        Directory directory = getDirectory(normalizedPath);
        StringBuilder builder = new StringBuilder();
        builder.append(normalizedPath).append('\n');
        appendTree(builder, directory, "");
        return builder.toString();
    }

    public List<String> journalTail(int limit) throws IOException {
        return journal.readTail(limit);
    }

    public static void main(String[] args) {
        try {
            FileSystemSimulator simulator = new FileSystemSimulator("filesystem.img", "filesystem.journal");

            if (args.length == 0 || "shell".equalsIgnoreCase(args[0])) {
                simulator.runShell();
                return;
            }

            simulator.executeCommand(Arrays.asList(args), "/");
        } catch (Exception exception) {
            System.err.println("Erro: " + exception.getMessage());
        }
    }

    private void runShell() {
        Scanner scanner = new Scanner(System.in);
        String currentDirectory = "/";

        System.out.println("Simulador de Sistema de Arquivos com Journaling");
        System.out.println("Digite 'help' para ver os comandos ou 'exit' para sair.");

        while (true) {
            System.out.print("fs:" + currentDirectory + "> ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine();
            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) {
                continue;
            }

            String command = tokens.get(0).toLowerCase();
            if ("exit".equals(command) || "quit".equals(command)) {
                break;
            }

            try {
                currentDirectory = executeCommand(tokens, currentDirectory);
            } catch (Exception exception) {
                System.out.println("Erro: " + exception.getMessage());
            }
        }
    }

    private String executeCommand(List<String> tokens, String currentDirectory) throws Exception {
        String command = tokens.get(0).toLowerCase();

        if ("help".equals(command)) {
            printHelp();
            return currentDirectory;
        }

        if ("pwd".equals(command)) {
            System.out.println(currentDirectory);
            return currentDirectory;
        }

        if ("cd".equals(command)) {
            requireArgumentCount(tokens, 2, "cd <diretorio>");
            String targetPath = resolvePath(currentDirectory, tokens.get(1));
            getDirectory(targetPath);
            return targetPath;
        }

        if ("mkdir".equals(command) || "createdir".equals(command)) {
            requireArgumentCount(tokens, 2, "mkdir <diretorio>");
            createDirectory(resolvePath(currentDirectory, tokens.get(1)));
            System.out.println("Diretorio criado.");
            return currentDirectory;
        }

        if ("touch".equals(command) || "createfile".equals(command)) {
            requireMinimumArgumentCount(tokens, 2, "touch <arquivo> [conteudo]");
            String content = tokens.size() > 2 ? joinTokens(tokens, 2) : "";
            createFile(resolvePath(currentDirectory, tokens.get(1)), content);
            System.out.println("Arquivo criado.");
            return currentDirectory;
        }

        if ("cp".equals(command) || "copy".equals(command)) {
            requireArgumentCount(tokens, 3, "cp <arquivo_origem> <destino>");
            copyFile(resolvePath(currentDirectory, tokens.get(1)), resolvePath(currentDirectory, tokens.get(2)));
            System.out.println("Arquivo copiado.");
            return currentDirectory;
        }

        if ("rm".equals(command) || "deletefile".equals(command)) {
            requireArgumentCount(tokens, 2, "rm <arquivo>");
            deleteFile(resolvePath(currentDirectory, tokens.get(1)));
            System.out.println("Arquivo apagado.");
            return currentDirectory;
        }

        if ("renamefile".equals(command) || "mvfile".equals(command)) {
            requireArgumentCount(tokens, 3, "renamefile <arquivo> <novo_nome>");
            renameFile(resolvePath(currentDirectory, tokens.get(1)), tokens.get(2));
            System.out.println("Arquivo renomeado.");
            return currentDirectory;
        }

        if ("rmdir".equals(command) || "deletedir".equals(command)) {
            requireArgumentCount(tokens, 2, "rmdir <diretorio>");
            deleteDirectory(resolvePath(currentDirectory, tokens.get(1)));
            System.out.println("Diretorio apagado.");
            return currentDirectory;
        }

        if ("renamedir".equals(command) || "mvdir".equals(command)) {
            requireArgumentCount(tokens, 3, "renamedir <diretorio> <novo_nome>");
            renameDirectory(resolvePath(currentDirectory, tokens.get(1)), tokens.get(2));
            System.out.println("Diretorio renomeado.");
            return currentDirectory;
        }

        if ("ls".equals(command) || "list".equals(command)) {
            String targetPath = tokens.size() > 1 ? resolvePath(currentDirectory, tokens.get(1)) : currentDirectory;
            List<String> entries = listDirectory(targetPath);
            printEntries(entries);
            return currentDirectory;
        }

        if ("tree".equals(command)) {
            String targetPath = tokens.size() > 1 ? resolvePath(currentDirectory, tokens.get(1)) : currentDirectory;
            System.out.print(tree(targetPath));
            return currentDirectory;
        }

        if ("journal".equals(command)) {
            int limit = tokens.size() > 1 ? Integer.parseInt(tokens.get(1)) : 20;
            printEntries(journalTail(limit));
            return currentDirectory;
        }

        throw new IllegalArgumentException("Comando desconhecido. Digite 'help'.");
    }

    private void executeJournaled(String operationName, List<String> arguments) throws IOException {
        Operation operation = journal.begin(operationName, arguments);
        apply(operation.getName(), operation.getArguments());
        journal.commit(operation.getTransactionId());
        lastAppliedTransactionId = operation.getTransactionId();
        saveSnapshot();
    }

    private void recoverFromJournal() throws IOException {
        int recoveredOperations = 0;
        List<Operation> operations = journal.readCommittedOperations();

        for (Operation operation : operations) {
            if (operation.getTransactionId() > lastAppliedTransactionId) {
                apply(operation.getName(), operation.getArguments());
                lastAppliedTransactionId = operation.getTransactionId();
                recoveredOperations++;
            }
        }

        if (recoveredOperations > 0) {
            saveSnapshot();
        }
    }

    private void apply(String operationName, List<String> arguments) {
        if (OP_CREATE_FILE.equals(operationName)) {
            expectArguments(operationName, arguments, 2);
            createFileInternal(arguments.get(0), arguments.get(1));
            return;
        }

        if (OP_COPY_FILE.equals(operationName)) {
            expectArguments(operationName, arguments, 2);
            copyFileInternal(arguments.get(0), arguments.get(1));
            return;
        }

        if (OP_DELETE_FILE.equals(operationName)) {
            expectArguments(operationName, arguments, 1);
            deleteFileInternal(arguments.get(0));
            return;
        }

        if (OP_RENAME_FILE.equals(operationName)) {
            expectArguments(operationName, arguments, 2);
            renameFileInternal(arguments.get(0), arguments.get(1));
            return;
        }

        if (OP_CREATE_DIRECTORY.equals(operationName)) {
            expectArguments(operationName, arguments, 1);
            createDirectoryInternal(arguments.get(0));
            return;
        }

        if (OP_DELETE_DIRECTORY.equals(operationName)) {
            expectArguments(operationName, arguments, 1);
            deleteDirectoryInternal(arguments.get(0));
            return;
        }

        if (OP_RENAME_DIRECTORY.equals(operationName)) {
            expectArguments(operationName, arguments, 2);
            renameDirectoryInternal(arguments.get(0), arguments.get(1));
            return;
        }

        throw new IllegalArgumentException("Operacao de journal desconhecida: " + operationName);
    }

    private void createFileInternal(String path, String content) {
        PathParts pathParts = splitPath(normalizePath(path));
        Directory parent = getDirectory(pathParts.parentPath);

        if (parent.hasChild(pathParts.name)) {
            throw new IllegalArgumentException("Ja existe arquivo ou diretorio com esse nome.");
        }

        parent.getFiles().put(pathParts.name, new File(pathParts.name, content));
    }

    private void copyFileInternal(String sourcePath, String destinationPath) {
        File source = getFile(normalizePath(sourcePath));
        String normalizedDestination = normalizePath(destinationPath);
        Directory destinationDirectory;
        String newName;

        if (directoryExists(normalizedDestination)) {
            destinationDirectory = getDirectory(normalizedDestination);
            newName = source.getName();
        } else {
            PathParts destinationParts = splitPath(normalizedDestination);
            destinationDirectory = getDirectory(destinationParts.parentPath);
            newName = destinationParts.name;
        }

        validateName(newName);
        if (destinationDirectory.hasChild(newName)) {
            throw new IllegalArgumentException("Destino ja existe.");
        }

        destinationDirectory.getFiles().put(newName, source.copy(newName));
    }

    private void deleteFileInternal(String path) {
        PathParts pathParts = splitPath(normalizePath(path));
        Directory parent = getDirectory(pathParts.parentPath);

        if (!parent.getFiles().containsKey(pathParts.name)) {
            throw new IllegalArgumentException("Arquivo nao encontrado.");
        }

        parent.getFiles().remove(pathParts.name);
    }

    private void renameFileInternal(String path, String newName) {
        PathParts pathParts = splitPath(normalizePath(path));
        validateName(newName);
        Directory parent = getDirectory(pathParts.parentPath);
        File file = parent.getFiles().get(pathParts.name);

        if (file == null) {
            throw new IllegalArgumentException("Arquivo nao encontrado.");
        }

        if (!pathParts.name.equals(newName) && parent.hasChild(newName)) {
            throw new IllegalArgumentException("Ja existe arquivo ou diretorio com o novo nome.");
        }

        parent.getFiles().remove(pathParts.name);
        file.setName(newName);
        parent.getFiles().put(newName, file);
    }

    private void createDirectoryInternal(String path) {
        String normalizedPath = normalizePath(path);
        if ("/".equals(normalizedPath)) {
            throw new IllegalArgumentException("O diretorio raiz ja existe.");
        }

        PathParts pathParts = splitPath(normalizedPath);
        Directory parent = getDirectory(pathParts.parentPath);

        if (parent.hasChild(pathParts.name)) {
            throw new IllegalArgumentException("Ja existe arquivo ou diretorio com esse nome.");
        }

        parent.getDirectories().put(pathParts.name, new Directory(pathParts.name));
    }

    private void deleteDirectoryInternal(String path) {
        String normalizedPath = normalizePath(path);
        if ("/".equals(normalizedPath)) {
            throw new IllegalArgumentException("O diretorio raiz nao pode ser apagado.");
        }

        PathParts pathParts = splitPath(normalizedPath);
        Directory parent = getDirectory(pathParts.parentPath);
        Directory directory = parent.getDirectories().get(pathParts.name);

        if (directory == null) {
            throw new IllegalArgumentException("Diretorio nao encontrado.");
        }

        if (!directory.isEmpty()) {
            throw new IllegalArgumentException("Diretorio precisa estar vazio para ser apagado.");
        }

        parent.getDirectories().remove(pathParts.name);
    }

    private void renameDirectoryInternal(String path, String newName) {
        String normalizedPath = normalizePath(path);
        if ("/".equals(normalizedPath)) {
            throw new IllegalArgumentException("O diretorio raiz nao pode ser renomeado.");
        }

        PathParts pathParts = splitPath(normalizedPath);
        validateName(newName);
        Directory parent = getDirectory(pathParts.parentPath);
        Directory directory = parent.getDirectories().get(pathParts.name);

        if (directory == null) {
            throw new IllegalArgumentException("Diretorio nao encontrado.");
        }

        if (!pathParts.name.equals(newName) && parent.hasChild(newName)) {
            throw new IllegalArgumentException("Ja existe arquivo ou diretorio com o novo nome.");
        }

        parent.getDirectories().remove(pathParts.name);
        directory.setName(newName);
        parent.getDirectories().put(newName, directory);
    }

    private File getFile(String normalizedPath) {
        PathParts pathParts = splitPath(normalizedPath);
        Directory parent = getDirectory(pathParts.parentPath);
        File file = parent.getFiles().get(pathParts.name);

        if (file == null) {
            throw new IllegalArgumentException("Arquivo nao encontrado: " + normalizedPath);
        }

        return file;
    }

    private Directory getDirectory(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return root;
        }

        Directory current = root;
        String[] segments = normalizedPath.substring(1).split("/");

        for (String segment : segments) {
            Directory next = current.getDirectories().get(segment);
            if (next == null) {
                throw new IllegalArgumentException("Diretorio nao encontrado: " + normalizedPath);
            }
            current = next;
        }

        return current;
    }

    private boolean directoryExists(String normalizedPath) {
        try {
            getDirectory(normalizedPath);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Caminho vazio.");
        }

        String rawPath = path.trim().replace('\\', '/');
        if (!rawPath.startsWith("/")) {
            throw new IllegalArgumentException("Use caminhos absolutos ou resolva pelo shell: " + path);
        }

        String[] rawSegments = rawPath.split("/");
        Deque<String> segments = new ArrayDeque<String>();

        for (String segment : rawSegments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }

            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }

            validateName(segment);
            segments.addLast(segment);
        }

        if (segments.isEmpty()) {
            return "/";
        }

        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            builder.append('/').append(segment);
        }
        return builder.toString();
    }

    private String resolvePath(String currentDirectory, String path) {
        if (path.startsWith("/")) {
            return normalizePath(path);
        }

        String separator = "/".equals(currentDirectory) ? "" : "/";
        return normalizePath(currentDirectory + separator + path);
    }

    private PathParts splitPath(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            throw new IllegalArgumentException("A raiz nao possui nome de arquivo.");
        }

        int lastSeparator = normalizedPath.lastIndexOf('/');
        String parentPath = lastSeparator == 0 ? "/" : normalizedPath.substring(0, lastSeparator);
        String name = normalizedPath.substring(lastSeparator + 1);
        validateName(name);
        return new PathParts(parentPath, name);
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome invalido.");
        }

        if (name.contains("/") || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("Nome invalido: " + name);
        }
    }

    private void loadSnapshot() throws Exception {
        ensureParentDirectory(imagePath);
        if (!Files.exists(imagePath) || Files.size(imagePath) == 0) {
            root = new Directory("");
            lastAppliedTransactionId = 0;
            saveSnapshot();
            return;
        }

        ObjectInputStream input = new ObjectInputStream(new FileInputStream(imagePath.toFile()));
        try {
            Snapshot snapshot = (Snapshot) input.readObject();
            root = snapshot.root;
            lastAppliedTransactionId = snapshot.lastAppliedTransactionId;
        } finally {
            input.close();
        }

        if (root == null) {
            root = new Directory("");
        }
    }

    private void saveSnapshot() throws IOException {
        ensureParentDirectory(imagePath);
        Path temporaryPath = Paths.get(imagePath.toString() + ".tmp");
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temporaryPath.toFile()));
        try {
            Snapshot snapshot = new Snapshot(root, lastAppliedTransactionId);
            output.writeObject(snapshot);
            output.flush();
        } finally {
            output.close();
        }

        try {
            Files.move(temporaryPath, imagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, imagePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureParentDirectory(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void appendTree(StringBuilder builder, Directory directory, String prefix) {
        for (Map.Entry<String, Directory> entry : directory.getDirectories().entrySet()) {
            builder.append(prefix).append("|-- ").append(entry.getKey()).append("/").append('\n');
            appendTree(builder, entry.getValue(), prefix + "|   ");
        }

        for (Map.Entry<String, File> entry : directory.getFiles().entrySet()) {
            builder.append(prefix).append("|-- ").append(entry.getKey()).append('\n');
        }
    }

    private void printEntries(List<String> entries) {
        if (entries.isEmpty()) {
            System.out.println("(vazio)");
            return;
        }

        for (String entry : entries) {
            System.out.println(entry);
        }
    }

    private static void printHelp() {
        System.out.println("Comandos disponiveis:");
        System.out.println("  mkdir <diretorio>");
        System.out.println("  touch <arquivo> [conteudo]");
        System.out.println("  cp <arquivo_origem> <destino>");
        System.out.println("  rm <arquivo>");
        System.out.println("  renamefile <arquivo> <novo_nome>");
        System.out.println("  rmdir <diretorio_vazio>");
        System.out.println("  renamedir <diretorio> <novo_nome>");
        System.out.println("  ls [diretorio]");
        System.out.println("  tree [diretorio]");
        System.out.println("  journal [quantidade_de_linhas]");
        System.out.println("  cd <diretorio>");
        System.out.println("  pwd");
        System.out.println("  exit");
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder currentToken = new StringBuilder();
        boolean insideQuotes = false;
        char quoteCharacter = '\0';

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if ((character == '"' || character == '\'') && !insideQuotes) {
                insideQuotes = true;
                quoteCharacter = character;
                continue;
            }

            if (insideQuotes && character == quoteCharacter) {
                insideQuotes = false;
                continue;
            }

            if (Character.isWhitespace(character) && !insideQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                continue;
            }

            currentToken.append(character);
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private static String joinTokens(List<String> tokens, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < tokens.size(); index++) {
            if (index > startIndex) {
                builder.append(' ');
            }
            builder.append(tokens.get(index));
        }
        return builder.toString();
    }

    private static void requireArgumentCount(List<String> tokens, int expectedCount, String usage) {
        if (tokens.size() != expectedCount) {
            throw new IllegalArgumentException("Uso: " + usage);
        }
    }

    private static void requireMinimumArgumentCount(List<String> tokens, int minimumCount, String usage) {
        if (tokens.size() < minimumCount) {
            throw new IllegalArgumentException("Uso: " + usage);
        }
    }

    private static void expectArguments(String operationName, List<String> arguments, int expectedCount) {
        if (arguments.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "Operacao " + operationName + " esperava " + expectedCount + " argumento(s).");
        }
    }

    private static class PathParts {
        private final String parentPath;
        private final String name;

        private PathParts(String parentPath, String name) {
            this.parentPath = parentPath;
            this.name = name;
        }
    }

    private static class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Directory root;
        private final long lastAppliedTransactionId;

        private Snapshot(Directory root, long lastAppliedTransactionId) {
            this.root = root;
            this.lastAppliedTransactionId = lastAppliedTransactionId;
        }
    }
}
