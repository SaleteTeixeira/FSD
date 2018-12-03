package common;

import java.util.Map;

public class GetServerReply {
    private final int transactionID;
    private final Map<Long, byte[]> values;

    public GetServerReply(final int transactionID, final Map<Long, byte[]> values) {
        this.transactionID = transactionID;
        this.values = values;
    }

    public int getTransactionID() { return this.transactionID; }

    public Map<Long, byte[]> getValues() {
        return this.values;
    }

    @Override
    public String toString() {
        return super.toString() +
                "GetServerReply{" +
                "values=" + this.values +
                '}';
    }
}
