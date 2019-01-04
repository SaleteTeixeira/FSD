package common;

import io.atomix.utils.net.Address;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface TwoPhaseCommitCoordinator extends common.TwoPhaseCommit {

    void handleTime(final Address origin, final byte[] bytes);

    void repeatProcess(final int reply_idT, String status);

    void handle2PC_Yes(final Address origin, final byte[] bytes);

    void handle2PC_No(final Address origin, final byte[] bytes);

    void handle2PC_Finished(final Address origin, final byte[] bytes);

    void writeLog(final int transactionID, final Collection<Address> participants, Address cliAddress, int cliRequestID);

    CompletableFuture<Boolean> prepared(final int transactionID);

    void finished(final int transactionID);

    int logLastTid();

    Collection<Address> logTidParticipants(final int idT);

    Address logTidCliAddress(final int idT);

    int logTidCliRequestID(final int idT);
}