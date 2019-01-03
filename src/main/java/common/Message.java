package common;

public abstract class Message {

    //request se for entre cliente-coordenador
    //transaction se for entre coordenador-servidor
    private final int req_tran_ID;

    Message(final int req_tran_ID) {
        this.req_tran_ID = req_tran_ID;
    }

    public int getReq_tran_ID() {
        return req_tran_ID;
    }

    @Override
    public String toString() {
        return "Message{" +
                "req_tran_ID = " + this.req_tran_ID +
                ", ";
    }
}