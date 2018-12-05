package common;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class GetRequest extends Message {
    private final Collection<Long> keys;

    public GetRequest(final int requestID, final AtomicInteger transactionID, final Collection<Long> keys) {
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
                "keys=" + this.keys +
                '}';
    }
}
