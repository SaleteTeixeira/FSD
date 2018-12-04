package common;

import java.util.Map;

public class GetReply extends Message {
    private final Map<Long, byte[]> values;

    public GetReply(final int requestID, final Map<Long, byte[]> values) {
        super(requestID);
        this.values = values;
    }

    public Map<Long, byte[]> getValues() {
        return this.values;
    }

    @Override
    public String toString() {
        return super.toString() +
                "GetReply{" +
                "keys=" + this.values +
                '}';
    }
}
