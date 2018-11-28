package common;

import java.util.Map;

public class PutRequest extends Message {
    private final Map<Long, byte[]> values;

    public PutRequest(final int clientID, final int transactionID, final Map<Long, byte[]> values) {
        super(clientID, transactionID);
        this.values = values;
    }

    public Map<Long, byte[]> getValues() {
        return this.values;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutRequest{" +
                "values=" + this.values +
                '}';
    }
}
