package coordinator;

import io.atomix.utils.serializer.Serializer;
import io.atomix.cluster.messaging.ManagedMessagingService;

import io.atomix.storage.journal.SegmentedJournal;
import io.atomix.storage.journal.SegmentedJournalReader;
import io.atomix.storage.journal.SegmentedJournalWriter;
import io.atomix.utils.net.Address;
import java.net.Inet4Address;
import java.util.concurrent.CompletableFuture;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TwoPhaseCommit implements common.TwoPhaseCommitCoordinator {

    private Serializer s, sl;
    private ManagedMessagingService ms;
    private SegmentedJournal<Object> log;
    private SegmentedJournalWriter<Object> writer;
    private SegmentedJournalReader<Object> reader;

    TwoPhaseCommit(Serializer s, ManagedMessagingService ms, String logName){
        this.s = s;
        this.ms = ms;
        this.sl = Serializer.builder()
                .withTypes(coordinator.CoordinatorLog.class)
                .withTypes(Address.class)
                .withTypes(Inet4Address.class)
                .withTypes(Address.Type.class)
                .withTypes(CompletableFuture.class)
                .build();
        this.log = SegmentedJournal.builder()
                .withName(logName)
                .withSerializer(sl)
                .build();
        this.writer = this.log.writer();
    }

    public void logToString(){
        System.out.println("\n----------LOG----------");

        this.reader = this.log.openReader(0);
        while (this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            System.out.println(e.toString());
        }

        System.out.println("-----------------------\n");
    }

    public void changeStatus(final int idT, final String status) {
        this.reader = this.log.openReader(0);
        Collection<Address> participants = null;
        Address cliAddress = null;
        int cliRequestID = -1;

        while (this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            if (e.getTransactionID() == idT) {
                participants = e.getParticipants();
                cliAddress = e.getCliAddress();
                cliRequestID = e.getCliRequestID();
            }
        }

        if (participants != null) {
            this.writer.append(new CoordinatorLog(idT, participants, status, cliAddress, cliRequestID));
            this.writer.flush();
        }
    }

    public void start(final int transactionID, final Collection<Address> participants, final Address cliAddress, final int cliRequestID) {
        this.writer.append(new CoordinatorLog(transactionID, participants, "", cliAddress, cliRequestID));
        this.writer.flush();
    }

    public void prepared(final int transactionID) {
        Collection<Address> participants = logTidParticipants(transactionID);

        for(Address a : participants){
            this.ms.sendAsync(a, "prepared", this.s.encode(transactionID));
        }

        new Thread(new timeCounter(transactionID, "")).start();
    }

    public void commit(final int transactionID) {
        Collection<Address> participants = logTidParticipants(transactionID);

        changeStatus(transactionID, "C");
        for (Address a : participants) {
            this.ms.sendAsync(a, "commit", this.s.encode(transactionID));
        }

        new Thread(new timeCounter(transactionID, "C")).start();
    }

    public void rollback(final int transactionID) {
        Collection<Address> participants = logTidParticipants(transactionID);

        changeStatus(transactionID, "A");
        for(Address a : participants){
            this.ms.sendAsync(a, "rollback", this.s.encode(transactionID));
        }

        new Thread(new timeCounter(transactionID, "A")).start();
    }

    public void finished(final int transactionID) {
        changeStatus(transactionID, "F");
    }

    public int logLastTid(){
        int transactionID = -1;
        this.reader = this.log.openReader(0);

        while(this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();

            if(e.getTransactionID()>transactionID) {
                transactionID = e.getTransactionID();
            }
        }

        return transactionID;
    }

    public synchronized Collection<Address> logTidParticipants(final int idT){
        this.reader = this.log.openReader(0);

        while (this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            if (e.getTransactionID() == idT) {
                return e.getParticipants();
            }
        }

        return null;
    }

    public String logTidLastStatus(final int idT){
        Map<Integer, String> logMap = new HashMap<>();
        this.reader = log.openReader(0);

        while(this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            logMap.put(e.getTransactionID(), e.getStatus());
        }

        return logMap.get(idT);
    }

    public Address logTidCliAddress(final int idT){
        this.reader = this.log.openReader(0);

        while (this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            if (e.getTransactionID() == idT) {
                return e.getCliAddress();
            }
        }

        return null;
    }

    public int logTidCliRequestID(final int idT){
        this.reader = this.log.openReader(0);

        while (this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            if (e.getTransactionID() == idT) {
                return e.getCliRequestID();
            }
        }

        return -1;
    }

    /** Restart of the coordinator:
     * Before 2PC started: Abort, as it might have missed a resource
     * During 2PC: Restart current phase by repeating the request to commit/rollback
     */
    public void restart() {
        Map<Integer, String> logMap = new HashMap<>();
        this.reader = log.openReader(0);

        while(this.reader.hasNext()) {
            CoordinatorLog e = (CoordinatorLog) this.reader.next().entry();
            logMap.put(e.getTransactionID(), e.getStatus());
        }

        for (final Map.Entry<Integer, String> s : logMap.entrySet()) {
            System.out.println("transactionID = "+s.getKey()+", status = "+s.getValue());

            switch (s.getValue()) {
                case "":
                    System.out.println("RESTART: transaction "+s.getKey()+" not started. Abort, as it might have missed a resource.");

                    this.rollback(s.getKey());
                    this.logToString();

                    System.out.println("Requested \"rollback\" for transaction "+s.getKey()+
                            " to server(s) "+this.logTidParticipants(s.getKey()).toString());
                    break;
                case "C":
                    System.out.println("RESTART: transaction "+s.getKey()+" started, but not committed by participants.\n" +
                            "Restart current phase by repeating the request to commit.");

                    this.commit(s.getKey());
                    this.logToString();

                    System.out.println("Requested \"commit\" for transaction "+s.getKey()+
                            " to server(s) "+this.logTidParticipants(s.getKey()).toString());
                    break;
                case "A":
                    System.out.println("RESTART: transaction "+s.getKey()+" started, but not aborted by participants.\n" +
                        "Restart current phase by repeating the request to rollback.");

                    this.rollback(s.getKey());
                    this.logToString();

                    System.out.println("Requested \"rollback\" for transaction "+s.getKey()+
                            " to server(s) "+this.logTidParticipants(s.getKey()).toString());
                    break;
                default:
                    System.out.println("NOTE: transaction "+s.getKey()+" finished.");
            }
        }
    }
}
