package common;

public interface TwoPhaseCommit {

    void changeStatus(final int idT, final String status);

    void commit(final int transactionID);

    void rollback(final int transactionID);

    void restart();

    void logToString();

    String logTidLastStatus(final int idT);
}
