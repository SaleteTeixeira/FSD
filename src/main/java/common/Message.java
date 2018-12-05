package common;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Message {
    private final int requestID;
    private final int transactionID;

    public Message(final int requestID, final AtomicInteger transactionID) {
        this.requestID = requestID;
        this.transactionID = transactionID.intValue();
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
                ", requestID=" + this.requestID +
                ", transactionID=" + this.transactionID +
                '}';
    }
}