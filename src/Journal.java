import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Journal {
    private final Path journalPath;
    private long nextTransactionId;

    public Journal(Path journalPath) throws IOException {
        this.journalPath = journalPath;
        ensureParentDirectory(journalPath);
        if (!Files.exists(journalPath)) {
            Files.createFile(journalPath);
        }
        this.nextTransactionId = findLastTransactionId() + 1;
    }

    public Operation begin(String operationName, List<String> arguments) throws IOException {
        Operation operation = new Operation(nextTransactionId, operationName, arguments);
        nextTransactionId++;
        appendLine(buildBeginLine(operation));
        return operation;
    }

    public void commit(long transactionId) throws IOException {
        appendLine("COMMIT|" + transactionId);
    }

    public List<Operation> readCommittedOperations() throws IOException {
        Map<Long, Operation> begunOperations = new HashMap<Long, Operation>();
        Set<Long> committedTransactions = new HashSet<Long>();

        BufferedReader reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("BEGIN|")) {
                    Operation operation = parseBeginLine(line);
                    begunOperations.put(operation.getTransactionId(), operation);
                } else if (line.startsWith("COMMIT|")) {
                    committedTransactions.add(parseTransactionId(line));
                }
            }
        } finally {
            reader.close();
        }

        List<Operation> operations = new ArrayList<Operation>();
        for (Map.Entry<Long, Operation> entry : begunOperations.entrySet()) {
            if (committedTransactions.contains(entry.getKey())) {
                operations.add(entry.getValue());
            }
        }

        operations.sort((first, second) -> Long.compare(first.getTransactionId(), second.getTransactionId()));
        return operations;
    }

    public List<String> readTail(int limit) throws IOException {
        List<String> lines = Files.readAllLines(journalPath, StandardCharsets.UTF_8);
        int start = Math.max(0, lines.size() - limit);
        return new ArrayList<String>(lines.subList(start, lines.size()));
    }

    public Path getJournalPath() {
        return journalPath;
    }

    private long findLastTransactionId() throws IOException {
        long lastTransactionId = 0;
        BufferedReader reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("BEGIN|")) {
                    lastTransactionId = Math.max(lastTransactionId, parseBeginLine(line).getTransactionId());
                } else if (line.startsWith("COMMIT|")) {
                    lastTransactionId = Math.max(lastTransactionId, parseTransactionId(line));
                }
            }
        } finally {
            reader.close();
        }
        return lastTransactionId;
    }

    private void appendLine(String line) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(
                journalPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private String buildBeginLine(Operation operation) {
        StringBuilder builder = new StringBuilder();
        builder.append("BEGIN|");
        builder.append(operation.getTransactionId());
        builder.append('|');
        builder.append(System.currentTimeMillis());
        builder.append('|');
        builder.append(operation.getName());

        for (String argument : operation.getArguments()) {
            builder.append('|');
            builder.append(encode(argument));
        }

        return builder.toString();
    }

    private Operation parseBeginLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Linha de journal invalida: " + line);
        }

        long transactionId = Long.parseLong(parts[1]);
        String operationName = parts[3];
        List<String> arguments = new ArrayList<String>();
        for (int index = 4; index < parts.length; index++) {
            arguments.add(decode(parts[index]));
        }

        return new Operation(transactionId, operationName, arguments);
    }

    private long parseTransactionId(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Linha de journal invalida: " + line);
        }
        return Long.parseLong(parts[1]);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void ensureParentDirectory(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
