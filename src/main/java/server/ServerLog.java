package server;

import common.Util;
import java.util.Map;

public class ServerLog {

    private int transactionID;
    private Map<Long, byte[]> values;
    private String status;

    ServerLog(int tID, Map<Long, byte[]> values, String status){
        this.transactionID = tID;
        this.values = values;
        this.status = status;
    }

    int getTransactionID() {
        return this.transactionID;
    }

    Map<Long, byte[]> getValues() {
        return this.values;
    }

    String getStatus() {
        return this.status;
    }

    public String toString(){
        return "ServerLog{ "+
                "transactionID = " + this.transactionID+", "+
                "values = " + Util.valuesToString(this.values)+", "+
                "status = " + this.status +
                "}";
    }
}
