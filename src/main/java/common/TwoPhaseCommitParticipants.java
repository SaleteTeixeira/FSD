package common;

import java.util.Map;

public interface TwoPhaseCommitParticipants extends TwoPhaseCommit {

    void start(final int transactionID, final Map<Long,byte[]> values);

    void prepared(final int transactionID, final String answer);

    server.Store restartStore();
}
