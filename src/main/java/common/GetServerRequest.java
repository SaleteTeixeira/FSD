package common;

import java.util.Collection;

public class GetServerRequest {
    private final int transactionID;
    private final Collection<Long> keys;

    public GetServerRequest(final int transactionID, final Collection<Long> keys) {
        this.transactionID = transactionID;
        this.keys = keys;
    }

    public int getTransactionID() { return this.transactionID; }

    public Collection<Long> getKeys() {
        return this.keys;
    }

    @Override
    public String toString() {
        return super.toString() +
                "GetServerRequest{" +
                "keys=" + this.keys +
                '}';
    }
}
