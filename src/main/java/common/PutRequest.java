package common;

import java.util.Map;

public class PutRequest extends Message {
    private final Map<Long, byte[]> values;

    public PutRequest(final int req_tran_ID, final Map<Long, byte[]> values) {
        super(req_tran_ID);
        this.values = values;
    }

    public Map<Long, byte[]> getValues() {
        return this.values;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutRequest{" +
                "values = " + Util.valuesToString(this.values) +
                "}}";
    }
}
