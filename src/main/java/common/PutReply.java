package common;

public class PutReply extends Message {
    private final boolean value;

    public PutReply(final int clientID, final int transactionID, final boolean value) {
        super(clientID, transactionID);
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
