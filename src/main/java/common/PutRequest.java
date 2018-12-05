package common;

import io.atomix.utils.net.Address;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PutRequest extends Message {
    private final Map<Long, byte[]> values;

    public PutRequest(final int requestID, final AtomicInteger transactionID, final Map<Long, byte[]> values) {
        super(requestID, transactionID);
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
