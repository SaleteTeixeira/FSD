package common;

public abstract class Message {
    private final int clientID;
    private final int transactionID;

    Message(final int clientID, final int transactionID) {
        this.clientID = clientID;
        this.transactionID = transactionID;
    }

    public int getClientID() {
        return this.clientID;
    }

    public int getTransactionID() {
        return this.transactionID;
    }

    @Override
    public String toString() {
        return "Message{" +
                "clientID=" + this.clientID +
                ", transactionID=" + this.transactionID +
                '}';
    }
}
