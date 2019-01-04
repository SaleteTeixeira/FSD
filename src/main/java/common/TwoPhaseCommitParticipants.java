package common;

import io.atomix.utils.net.Address;

import java.util.Map;

public interface TwoPhaseCommitParticipants extends TwoPhaseCommit {

    void handle2PC_Prepared(final Address origin, final byte[] bytes);

    void handle2PC_Commit(final Address origin, final byte[] bytes);

    void handle2PC_Rollback(final Address origin, final byte[] bytes);

    void writeLog(final int transactionID, final Map<Long,byte[]> values);

    void prepared(final int transactionID, final String answer);

    server.Store restartStore();
}
