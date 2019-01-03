package coordinator;

import io.atomix.utils.net.Address;
import java.util.Collection;

public class CoordinatorLog {

    private int transactionID;
    private Collection<Address> participants;
    private String status;
    private Address cliAddress;
    private int cliRequestID;

    CoordinatorLog(int tID, Collection<Address> participants, String status, Address cliAddress, int cliRequestID){
        this.transactionID = tID;
        this.participants = participants;
        this.status = status;
        this.cliAddress = cliAddress;
        this.cliRequestID = cliRequestID;
    }

    int getTransactionID() {
        return this.transactionID;
    }

    Collection<Address> getParticipants() {
        return this.participants;
    }

    String getStatus() {
        return status;
    }

    Address getCliAddress() {
        return cliAddress;
    }

    int getCliRequestID() {
        return cliRequestID;
    }

    public String toString(){
        return "CoordinatorLog{ "+
                "transactionID = " + this.transactionID+", "+
                "participants = " + this.participants.toString()+", "+
                "cliAddress = " + this.cliAddress+", "+
                "cliRequestID = " + this.cliRequestID+", "+
                "status = " + this.status +
                "}";
    }
}