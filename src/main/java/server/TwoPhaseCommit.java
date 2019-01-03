package server;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.storage.journal.SegmentedJournal;
import io.atomix.storage.journal.SegmentedJournalReader;
import io.atomix.storage.journal.SegmentedJournalWriter;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class TwoPhaseCommit implements common.TwoPhaseCommitParticipants {

    private Serializer s, sl;
    private ManagedMessagingService ms;
    private ExecutorService es;
    private final Address coordAdd;
    private SegmentedJournal<Object> log;
    private SegmentedJournalWriter<Object> writer;
    SegmentedJournalReader<Object> reader;

    TwoPhaseCommit(Serializer s, ManagedMessagingService ms, ExecutorService es, Address coordAdd, String logName){
        this.s = s;
        this.ms = ms;
        this.es = es;
        this.coordAdd = coordAdd;
        this.sl = Serializer.builder()
                .withTypes(ServerLog.class)
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
            ServerLog e = (ServerLog) this.reader.next().entry();
            System.out.println(e.toString());
        }

        System.out.println("-----------------------\n");
    }

    public void changeStatus(int idT, String status) {
        this.reader = this.log.openReader(0);
        Map<Long, byte[]> values = null;

        while (this.reader.hasNext()) {
            ServerLog e = (ServerLog) this.reader.next().entry();
            if (e.getTransactionID() == idT) {
                values = e.getValues();
            }
        }

        if (values != null) {
            this.writer.append(new ServerLog(idT, values, status));
            this.writer.flush();
        }
    }

    public void start(final int transactionID, Map<Long,byte[]> values) {
        this.writer.append(new ServerLog(transactionID, values, ""));
        this.writer.flush();
    }

    public void prepared(int transactionID, String answer) {
        changeStatus(transactionID, "P");
        this.ms.sendAsync(this.coordAdd, answer, this.s.encode(transactionID));
    }

    public void commit(int transactionID) {
        changeStatus(transactionID, "C");
        this.ms.sendAsync(this.coordAdd, "finished", this.s.encode(transactionID));
    }

    public void rollback(int transactionID){
        changeStatus(transactionID, "A");
        this.ms.sendAsync(this.coordAdd, "finished", this.s.encode(transactionID));
    }

    public String logTidLastStatus(final int idT){
        Map<Integer, String> logMap = new HashMap<>();
        this.reader = log.openReader(0);

        while(this.reader.hasNext()) {
            ServerLog e = (ServerLog) this.reader.next().entry();
            logMap.put(e.getTransactionID(), e.getStatus());
        }

        return logMap.get(idT);
    }

    public Store restartStore() {
        Store store = new Store();
        Map<Integer, String> logState = new HashMap<>();
        Map<Integer, Map<Long,byte[]>> logValues = new HashMap<>();

        this.reader = log.openReader(0);

        while(this.reader.hasNext()) {
            ServerLog e = (ServerLog) this.reader.next().entry();
            logState.put(e.getTransactionID(), e.getStatus());
            logValues.put(e.getTransactionID(), e.getValues());
        }

        for(Map.Entry<Integer, String> e : logState.entrySet()){
            switch (e.getValue()){
                case "A": //não se adiciona
                    break;
                default: //adiciona-se no caso "" porque, apesar de ficar A no restart, ainda pode vir a ficar C a pedido do coordenador
                    store.put(logValues.get(e.getKey()), e.getKey());
                    break;
            }
        }

        return store;
    }

    /** Restart of a participant:
     * Has not voted: Local rollback, will abort the entire transaction
     * Has voted: Wait for the decision from the coordinator
     */
    public void restart(){
        Map<Integer, String> logMap = new HashMap<>();
        this.reader = log.openReader(0);

        while(this.reader.hasNext()) {
            ServerLog e = (ServerLog) this.reader.next().entry();
            logMap.put(e.getTransactionID(), e.getStatus());
        }

        for (final Map.Entry<Integer, String> s : logMap.entrySet()) {
            System.out.println("transactionID = "+s.getKey()+", status = \""+s.getValue()+"\"");

            switch (s.getValue()) {
                case "":
                    System.out.println("RESTART: transaction "+s.getKey()+" not voted. Local rollback.");
                    changeStatus(s.getKey(), "A");
                    break;
                case "P":
                    System.out.println("RESTART: transaction "+s.getKey()+" voted. Waiting for the decision from the coordinator.");
                    break;
                default:
                    System.out.println("NOTE: transaction "+s.getKey()+" finished.");
            }
        }
    }
}
