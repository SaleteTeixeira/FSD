package common;

import java.util.Collection;

public class GetRequest extends Message {
    private final Collection<Long> keys;

    public GetRequest(final int requestID, final int transactionID, final Collection<Long> keys) {
        super(requestID, transactionID);
        this.keys = keys;
    }

    public Collection<Long> getKeys() {
        return this.keys;
    }

    @Override
    public String toString() {
        return super.toString() +
                "GetRequest{" +
                "keys=" + this.keys.toString() +
                "}}";
    }
}
