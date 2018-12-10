package common;

import java.util.Map;

public class PutRequest extends Message {
    private final Map<Long, byte[]> values;

    public PutRequest(final int requestID, final int transactionID, final Map<Long, byte[]> values) {
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
                "values = " + print(this.values) +
                "}}";
    }

    private String print(Map<Long, byte[]> values){
        StringBuilder s = new StringBuilder();

        for (Map.Entry<Long, byte[]> a : values.entrySet()){
            s.append(a.getKey());
            String b = new String(a.getValue());
            s.append(" ").append(b).append(", ");
        }

        return s.toString();
    }
}
