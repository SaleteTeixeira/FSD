package coordinator;

import common.PutReply;
import io.atomix.utils.serializer.Serializer;
import io.atomix.cluster.messaging.ManagedMessagingService;

import io.atomix.storage.journal.SegmentedJournal;
import io.atomix.storage.journal.SegmentedJournalReader;
import io.atomix.storage.journal.SegmentedJournalWriter;
import io.atomix.utils.net.Address;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class TwoPhaseCommit implements common.TwoPhaseCommitCoordinator {

    private class Contador {
        private final int nr_a_receber;
        private int nr_recebido;
        private boolean put_answers;

        Contador(final int nr_a_receber) {
            this.nr_a_receber = nr_a_receber;
            this.nr_recebido = 0;
            this.put_answers = true;
        }

        int getNr_a_receber(){
            return this.nr_a_receber;
        }

        int getNr_recebido(){
            return this.nr_recebido;
        }

        void setNr_recebido(int nr){
            this.nr_recebido = nr;
        }

        boolean getPut_answers() {
            return this.put_answers;
        }

        void setPut_answers(final boolean put_answers) {
            this.put_answers = put_answers;
        }

        boolean finished() {
            return this.nr_a_receber == this.nr_recebido;
        }

        void increment_nr_recebido() {
            this.nr_recebido++;
        }
    }

    private class State_NrTimes{
        private String state;
        private int nrTimes;

        private State_NrTimes(String state, int nrTimes) {
            this.state = state;
            this.nrTimes = nrTimes;
        }

        String getState() {
            return state;
        }
    }

    private Serializer s, sl;
    private ManagedMessagingService ms;
    private SegmentedJournal<Object> log;
    private SegmentedJournalWriter<Object> writer;
    private SegmentedJournalReader<Object> reader;

    private Map<Integer, CompletableFuture<Boolean>> completableFutures;
    private Map<Integer, Collection<Address>> participants;
    private Map<Integer, Contador> counter;
    private Map<Integer, State_NrTimes> putTimeCount;

    TwoPhaseCommit(Serializer s, ManagedMessagingService ms, ExecutorService es, String logName){
        this.s = s;
        this.ms = ms;

        this.ms.registerHandler("yes", this::handle2PC_Yes, es);
        this.ms.registerHandler("no", this::handle2PC_No, es);
        this.ms.registerHandler("finished", this::handle2PC_Finished, es);
        this.ms.registerHandler("time", this::handleTime, es);

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

        this.completableFutures = new HashMap<>();
        this.participants = new HashMap<>();
        this.counter = new HashMap<>();
        this.putTimeCount = new HashMap<>();

        //LOG -> reiniciar
        for(int i=0; i<=this.logLastTid(); i++){
            //reiniciar participantes
            Collection<Address> aux = this.logTidParticipants(i);
            Collection<Address> pTid = new ArrayList<>();

            if(aux!=null){//null é porque esse ID era de um GET e por isso não ficou em log
                for(Address a : aux){
                    pTid.add(Address.from("localhost:"+a.port()));
                }

                this.participants.put(i, pTid);
                //reiniciar contadores
                this.counter.put(i, new Contador(aux.size()));
            }
        }
    }

    public void handleTime(final Address origin, final byte[] bytes) {
        String statusTid = this.s.decode(bytes);
        String status = statusTid.substring(0,1);
        if(status.equals(" ")) status="";
        int reply_idT = Integer.parseInt(statusTid.substring(2,3));

        final Contador c = this.counter.get(reply_idT);

        if((this.logTidLastStatus(reply_idT).equals("") && status.equals("") && c.getNr_recebido()<c.getNr_a_receber())
                || (this.logTidLastStatus(reply_idT).equals("C") && status.equals("C") && c.getNr_recebido()<c.getNr_a_receber())
                || (this.logTidLastStatus(reply_idT).equals("A") && status.equals("A") && c.getNr_recebido()<c.getNr_a_receber())
        ){
            System.out.println("TIMER: time finished and someone did not answer to status \""+status+"\" for transaction "+reply_idT);
            this.repeatProcess(reply_idT, status);
        }
        else{
            System.out.println("TIMER: finished counting and everyone has already answered to status \""+status+"\" for transaction "+reply_idT);
        }
    }

    //segunda e última tentativa
    public void repeatProcess(final int reply_idT, String status) {
        final Contador c = this.counter.get(reply_idT);
        final State_NrTimes sNt = this.putTimeCount.get(reply_idT);

        if(sNt==null || !sNt.getState().equals(status)) {
            this.putTimeCount.put(reply_idT, new State_NrTimes(status,2));
            System.out.println("Repeat process.");

            switch (status){
                case "":
                    this.prepared(reply_idT);
                    break;
                case "C":
                    this.commit(reply_idT);
                    c.setPut_answers(true);
                    break;
                case "A":
                    this.rollback(reply_idT);
                    c.setPut_answers(false);
                    break;
                default:
                    System.out.println("Invalid state");
            }

            c.setNr_recebido(0);

            this.logToString();
            System.out.println("Requested \""+status+"\" for transaction "+reply_idT+
                    " to server(s) "+this.participants.get(reply_idT).toString()+" FOR THE LAST TIME");
        }
        else {
            this.rollback(reply_idT);
            c.setPut_answers(false);

            this.finished(reply_idT);
            this.logToString();

            CompletableFuture<Boolean> cf = this.completableFutures.get(reply_idT);
            if (cf!=null) cf.complete(false);
            else { //significa que essa transação veio de um restart
                Address cliAddress = this.logTidCliAddress(reply_idT);
                int cliRequestID = this.logTidCliRequestID(reply_idT);
                ms.sendAsync(cliAddress, "put", s.encode(new PutReply(cliRequestID, false)));
            }

            System.out.println("No more tries! Aborting and sending to client put = false");
        }
    }

    public void handle2PC_Yes(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.participants.get(reply_idT).size() && !stop; i++) {
            try {
                if (this.participants.get(reply_idT).contains(origin)
                        && !this.logTidLastStatus(reply_idT).equals("A")
                        && !this.logTidLastStatus(reply_idT).equals("C")
                        && !this.logTidLastStatus(reply_idT).equals("F")) {
                    //se ainda não tiver recebido nenhum pedido de "abort"
                    //ou feito "abort" pelo restart
                    //ou feito "abort"/"commit"/"finished" no repeatProcess

                    stop = true;

                    final Contador c = this.counter.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a YES from "+origin+" for transaction "+reply_idT+
                            " ("+c.getNr_recebido()+"/"+c.getNr_a_receber()+")");

                    if (c.finished()) {
                        this.commit(reply_idT);
                        c.setPut_answers(true);
                        c.setNr_recebido(0);

                        this.logToString();
                        System.out.println("Requested \"commit\" for transaction " + reply_idT +
                                " to server(s) " + this.participants.get(reply_idT).toString());
                    }
                }
            } catch (NullPointerException ignored){}
        }
    }

    public void handle2PC_No(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.participants.get(reply_idT).size() && !stop; i++) {
            try {
                if (this.participants.get(reply_idT).contains(origin)
                        && !this.logTidLastStatus(reply_idT).equals("A")
                        && !this.logTidLastStatus(reply_idT).equals("C")
                        && !this.logTidLastStatus(reply_idT).equals("F")) {
                    //se ainda não tiver recebido nenhum pedido de "abort"
                    //ou feito "abort" pelo restart
                    //ou feito "abort"/"commit"/"finished" no repeatProcess

                    stop = true;

                    final Contador c = this.counter.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a NO from "+origin+" for transaction "+reply_idT);

                    this.rollback(reply_idT);
                    c.setPut_answers(false);
                    c.setNr_recebido(0);

                    this.logToString();
                    System.out.println("Requested \"rollback\" for transaction "+reply_idT+
                            " to server(s) "+this.participants.get(reply_idT).toString());
                }
            } catch (NullPointerException ignored){}
        }
    }

    public void handle2PC_Finished(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.participants.get(reply_idT).size() && !stop; i++) {
            try {
                if (this.participants.get(reply_idT).contains(origin)
                        && !this.logTidLastStatus(reply_idT).equals("F")) { //pelo repeatProcess

                    /**testar caso em que o Coordenador reinicia com status="C" ou status="A"*/
                    /*try {
                        System.out.println("Going to sleep with status=\"C\" or status=\"A\"");
                        Thread.sleep(5000);
                        System.out.println("Woke up!!!");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/

                    stop = true;

                    final Contador c = this.counter.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a FINISHED from "+origin+" for transaction "+reply_idT+
                            " ("+c.getNr_recebido()+"/"+c.getNr_a_receber()+")");

                    if (c.finished()) {
                        if(this.logTidLastStatus(reply_idT).equals("A")){
                            c.setPut_answers(false); //porque restart que não coloca a falso no contador
                        }

                        this.finished(reply_idT);

                        CompletableFuture<Boolean> cf = this.completableFutures.get(reply_idT);
                        if (cf!=null) {
                            cf.complete(c.getPut_answers());
                        }
                        else { //significa que essa transação veio de um restart
                            Address cliAddress = this.logTidCliAddress(reply_idT);
                            int cliRequestID = this.logTidCliRequestID(reply_idT);
                            ms.sendAsync(cliAddress, "put", s.encode(new PutReply(cliRequestID, c.getPut_answers())));
                        }

                        this.logToString();
                        System.out.println("All participants finished 2PC for transaction "+reply_idT+
                                ". Sending to client put = "+c.getPut_answers());
                    }
                }
            } catch (NullPointerException ignored){}
        }
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

    public void writeLog(final int transactionID, final Collection<Address> participants, final Address cliAddress, final int cliRequestID) {
        this.writer.append(new CoordinatorLog(transactionID, participants, "", cliAddress, cliRequestID));
        this.writer.flush();

        this.participants.put(transactionID, participants);
        this.counter.put(transactionID, new Contador(participants.size()));
    }

    public CompletableFuture<Boolean> prepared(final int transactionID) {
        final CompletableFuture<Boolean> t = new CompletableFuture<>();



        for(Address a : this.participants.get(transactionID)){
            this.ms.sendAsync(a, "prepared", this.s.encode(transactionID));
        }

        new Thread(new timeCounter(transactionID, "")).start();

        if(this.completableFutures.get(transactionID)==null){ //porque a repetição também faz prepared
            this.completableFutures.put(transactionID, t);
            this.logToString();
            System.out.println("Requested \"prepared\" for transaction "+transactionID+
                    " to server(s) "+this.participants.get(transactionID).toString());
        }

        return t;
    }

    public void commit(final int transactionID) {
        changeStatus(transactionID, "C");

        for(Address a : this.participants.get(transactionID)){
            this.ms.sendAsync(a, "commit", this.s.encode(transactionID));
        }

        new Thread(new timeCounter(transactionID, "C")).start();
    }

    public void rollback(final int transactionID) {
        changeStatus(transactionID, "A");

        for(Address a : this.participants.get(transactionID)){
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