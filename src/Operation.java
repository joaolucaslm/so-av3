import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Operation {
    private final long transactionId;
    private final String name;
    private final List<String> arguments;

    public Operation(long transactionId, String name, List<String> arguments) {
        this.transactionId = transactionId;
        this.name = name;
        this.arguments = new ArrayList<String>(arguments);
    }

    public long getTransactionId() {
        return transactionId;
    }

    public String getName() {
        return name;
    }

    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }
}
