package common;

public abstract class Message {
    private final int requestID;

    public Message(final int requestID) {
        this.requestID = requestID;
    }

    public int getRequestID() {
        return this.requestID;
    }

    @Override
    public String toString() {
        return "Message{" +
                ", requestID=" + this.requestID +
                '}';
    }
}
