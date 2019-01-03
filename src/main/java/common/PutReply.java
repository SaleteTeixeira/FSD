package common;

public class PutReply extends Message {
    private final boolean value;

    public PutReply(final int req_tran_ID, final boolean value) {
        super(req_tran_ID);
        this.value = value;
    }

    public boolean getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutReply{" +
                "value = " + this.value +
                "}}";
    }
}
