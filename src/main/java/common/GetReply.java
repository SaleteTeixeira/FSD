package common;

import java.util.Map;

public class GetReply extends Message {
    private final Map<Long, byte[]> keys;

    public GetReply(final int clientID, final int transactionID, final Map<Long, byte[]> values) {
        super(clientID, transactionID);
        this.keys = values;
    }

    public Map<Long, byte[]> getKeys() {
        return this.keys;
    }

    @Override
    public String toString() {
        return super.toString() +
                "GetReply{" +
                "keys=" + this.keys +
                '}';
    }
}
