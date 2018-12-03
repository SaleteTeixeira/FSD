package common;

import java.util.Map;

public class PutServerRequest {
    private final int transactionID;
    private final Map<Long, byte[]> values;

    public PutServerRequest(final int transactionID, final Map<Long, byte[]> values) {
        this.transactionID = transactionID;
        this.values = values;
    }

    public int getTransactionID() { return this.transactionID;}

    public Map<Long, byte[]> getValues() {
        return this.values;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutServerRequest{" +
                "values=" + this.values +
                '}';
    }
}
