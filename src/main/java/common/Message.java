package common;

public abstract class Message {
    private final int requestID;
    private final int transactionID;

    public Message(final int requestID, final int transactionID) {
        this.requestID = requestID;
        this.transactionID = transactionID;
    }

    public int getRequestID() {
        return this.requestID;
    }

    public int getTransactionID() {
        return this.transactionID;
    }

    @Override
    public String toString() {
        return "Message{" +
                "requestID=" + this.requestID +
                ", transactionID=" + this.transactionID +
                ", ";
    }
}