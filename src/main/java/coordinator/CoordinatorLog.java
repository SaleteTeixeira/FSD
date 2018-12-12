package coordinator;

public class CoordinatorLog {
    private int transactionID;

    public CoordinatorLog(int tID){
        this.transactionID = tID;
    }

    public int getTransactionID() {
        return transactionID;
    }

    public String toString(){
        return "transactionID: "+transactionID+"\n";
    }
}
