package common;

public class PutReply extends Message {
    private final boolean value;

    public PutReply(final int requestID, final boolean value) {
        super(requestID);
        this.value = value;
    }

    public boolean getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutReply{" +
                "value=" + this.value +
                '}';
    }
}
