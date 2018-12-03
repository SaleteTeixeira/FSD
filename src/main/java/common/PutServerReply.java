package common;

public class PutServerReply {
    private final int transactionID;
    private final boolean result;

    public PutServerReply(final int transactionID, final boolean result) {
        this.transactionID = transactionID;
        this.result = result;
    }

    public int getTransactionID() { return  this.transactionID;}

    public boolean getResult() {
        return this.result;
    }

    @Override
    public String toString() {
        return super.toString() +
                "PutServerReply{" +
                "result=" + this.result +
                '}';
    }
}
