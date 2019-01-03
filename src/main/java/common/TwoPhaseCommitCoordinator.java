package common;

import io.atomix.utils.net.Address;
import java.util.Collection;

public interface TwoPhaseCommitCoordinator extends common.TwoPhaseCommit {

    void start(final int transactionID, final Collection<Address> participants, Address cliAddress, int cliRequestID);

    void prepared(final int transactionID);

    void finished(final int transactionID);

    int logLastTid();

    Collection<Address> logTidParticipants(final int idT);

    Address logTidCliAddress(final int idT);

    int logTidCliRequestID(final int idT);
}