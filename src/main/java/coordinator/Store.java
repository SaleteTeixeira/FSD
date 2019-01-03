package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Store {

    private class Contador {
        private final int nr_a_receber;
        private int nr_recebido;
        private boolean put_answers;
        private final Map<Long, byte[]> get_answers;

        Contador(final int nr_a_receber) {
            this.nr_a_receber = nr_a_receber;
            this.nr_recebido = 0;
            this.put_answers = true;
            this.get_answers = new HashMap<>();
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

        Map<Long, byte[]> getGet_answers() {
            return this.get_answers;
        }

        void setGet_answers(final Map<Long, byte[]> get_answers) {
            for (final Map.Entry<Long, byte[]> s : get_answers.entrySet()) {
                this.get_answers.put(s.getKey(), s.getValue());
            }
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

    private final Serializer s;
    private final ManagedMessagingService ms;
    private final Address[] servers;

    private TwoPhaseCommit twoPC;
    private final Map<Integer, State_NrTimes> putTimeCount;
    private final Map<Integer, Collection<Address>> participants;

    private int transactionID;
    private final Map<Integer, CompletableFuture<Boolean>> putCompletableFutures;
    private final Map<Integer, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;
    private final Map<Integer, Contador> putCompletableFuturesCount;
    private final Map<Integer, Contador> getCompletableFuturesCount;

    Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:22222"))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();

        this.servers = new Address[]{Address.from("localhost:12345"), Address.from("localhost:12346"), Address.from("localhost:12347")};

        this.ms.registerHandler("put", this::handlePut, es);
        this.ms.registerHandler("get", this::handleGet, es);
        this.ms.registerHandler("yes", this::handle2PC_Yes, es);
        this.ms.registerHandler("no", this::handle2PC_No, es);
        this.ms.registerHandler("finished", this::handle2PC_Finished, es);
        this.ms.registerHandler("time", this::handleTime, es);

        this.twoPC = new TwoPhaseCommit(this.s, this.ms, "LOG - coordinator");
        this.twoPC.logToString();

        this.putTimeCount = new HashMap<>();

        //LOG -> reiniciar -> id da última transação guardada
        this.transactionID = twoPC.logLastTid();
        this.participants = new HashMap<>();
        this.putCompletableFutures = new HashMap<>();
        this.putCompletableFuturesCount = new HashMap<>();

        //LOG -> reiniciar
        for(int i=0; i<=this.transactionID; i++){
            //reiniciar participantes
            Collection<Address> aux = this.twoPC.logTidParticipants(i);
            Collection<Address> pTid = new ArrayList<>();

            if(aux!=null){//null é porque esse ID era de um GET e por isso não ficou em log
                for(Address a : aux){
                    pTid.add(Address.from("localhost:"+a.port()));
                }

                this.participants.put(i, pTid);
                //reiniciar contadores
                this.putCompletableFuturesCount.put(i, new Contador(aux.size()));
            }
        }

        this.getCompletableFutures = new HashMap<>();
        this.getCompletableFuturesCount = new HashMap<>();

        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("⇢ Restart coordinator");
        this.twoPC.restart();
    }

    public CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        final CompletableFuture<Map<Long, byte[]>> t = new CompletableFuture<>();
        this.transactionID++;
        this.getCompletableFutures.put(this.transactionID, t);

        final Map<Address, Collection<Long>> temp = new HashMap<>();

        keys.forEach(k -> {
            final Address key = this.servers[(int) (k % (this.servers.length))];
            if (!temp.containsKey(key)) {
                temp.put(key, new HashSet<>());
            }
            temp.get(key).add(k);
        });

        if (temp.size() == 0) {
            t.complete(new HashMap<>());
            return t;
        }

        this.getCompletableFuturesCount.put(this.transactionID, new Contador(temp.size()));
        this.participants.put(this.transactionID, new HashSet<>(temp.keySet()));

        temp.forEach((k, v) -> {
            if (v.size() > 0) {
                this.ms.sendAsync(k, "get", this.s.encode(new GetRequest(this.transactionID, temp.get(k))));
            }
        });

        return t;
    }

    public CompletableFuture<Boolean> put(final Address cliAddress, final int cliRequestID, final Map<Long, byte[]> values) {
        final CompletableFuture<Boolean> t = new CompletableFuture<>();
        this.transactionID++;
        this.putCompletableFutures.put(this.transactionID, t);

        final Map<Address, Map<Long, byte[]>> temp = new HashMap<>();

        values.forEach((k, v) -> {
            final Address key = this.servers[(int) (k % (this.servers.length))];
            if (!temp.containsKey(key)) {
                temp.put(key, new HashMap<>());
            }
            temp.get(key).put(k, v);
        });

        if (temp.size() == 0) {
            t.complete(true);
            return t;
        }

        this.putCompletableFuturesCount.put(this.transactionID, new Contador(temp.size()));
        this.participants.put(this.transactionID, new HashSet<>(temp.keySet()));
        twoPC.start(this.transactionID, this.participants.get(this.transactionID), cliAddress, cliRequestID);
        twoPC.logToString();

        temp.forEach((k, v) -> {
            if (v.size() > 0) {
                this.ms.sendAsync(k, "put", this.s.encode(new PutRequest(this.transactionID, temp.get(k))));
            }
        });

        return t;
    }

    private void handleGet(final Address origin, final byte[] bytes) {
        boolean stop = false;
        final GetReply reply = this.s.decode(bytes);

        for (int i = 0; i < this.servers.length && !stop; i++) {
            try{
                if (origin.equals(this.servers[i]) && this.participants.get(reply.getReq_tran_ID()).contains(origin)) {
                    stop = true;

                    final Contador c = this.getCompletableFuturesCount.get(reply.getReq_tran_ID());
                    c.increment_nr_recebido();
                    c.setGet_answers(reply.getValues());

                    if (c.finished()) {
                        this.getCompletableFutures.get(reply.getReq_tran_ID()).complete(c.getGet_answers());

                        System.out.println("All participants finished GET for transaction "+reply.getReq_tran_ID()+
                                ". Sending to client get = "+Util.valuesToString(c.getGet_answers()));
                    }
                }
            } catch (NullPointerException ignored){}
        }
    }

    //usa log
    private void handlePut(final Address origin, final byte[] bytes) {
        boolean stop = false;
        final PutReply reply = this.s.decode(bytes);

        for (int i = 0; i < this.servers.length && !stop; i++) {
            try{
                if (origin.equals(this.servers[i]) && this.participants.get(reply.getReq_tran_ID()).contains(origin)) {

                    /**testar caso em que o Coordenador reinicia com status=""*/
                    /*try {
                        System.out.println("Going to sleep with status=\" \"");
                        Thread.sleep(5000);
                        System.out.println("Woke up!!!");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/

                    stop = true;
                    int reply_idT = reply.getReq_tran_ID();

                    final Contador c = this.putCompletableFuturesCount.get(reply_idT);
                    c.increment_nr_recebido();
                    c.setPut_answers(reply.getValue());

                    System.out.println("Recieved an answer to PUT from "+origin+" for transaction "+reply_idT+
                            " ("+c.getNr_recebido()+"/"+c.getNr_a_receber()+")");

                    if (c.finished()) {
                        this.twoPC.prepared(reply_idT);
                        c.setNr_recebido(0);

                        this.twoPC.logToString();
                        System.out.println("Requested \"prepared\" for transaction "+reply_idT+
                                " to server(s) "+this.participants.get(reply_idT).toString());
                    }
                }
            } catch (NullPointerException ignored){}
        }
    }

    private void handleTime(final Address origin, final byte[] bytes) {
        String statusTid = this.s.decode(bytes);
        String status = statusTid.substring(0,1);
        if(status.equals(" ")) status="";
        int reply_idT = Integer.parseInt(statusTid.substring(2,3));

        final Contador c = this.putCompletableFuturesCount.get(reply_idT);

        if((this.twoPC.logTidLastStatus(reply_idT).equals("") && status.equals("") && c.getNr_recebido()<c.getNr_a_receber())
            || (this.twoPC.logTidLastStatus(reply_idT).equals("C") && status.equals("C") && c.getNr_recebido()<c.getNr_a_receber())
            || (this.twoPC.logTidLastStatus(reply_idT).equals("A") && status.equals("A") && c.getNr_recebido()<c.getNr_a_receber())
          ){
            System.out.println("TIMER: time finished and someone did not answer to status \""+status+"\" for transaction "+reply_idT);
            this.repeatProcess(reply_idT, status);
        }
        else{
            System.out.println("TIMER: finished counting and everyone has already answered to status \""+status+"\" for transaction "+reply_idT);
        }
    }

    //segunda e última tentativa
    private void repeatProcess(final int reply_idT, String status) {
        final Contador c = this.putCompletableFuturesCount.get(reply_idT);
        final State_NrTimes sNt = this.putTimeCount.get(reply_idT);

        if(sNt==null || !sNt.getState().equals(status)) {
            this.putTimeCount.put(reply_idT, new State_NrTimes(status,2));
            System.out.println("Repeat process.");

            switch (status){
                case "":
                    this.twoPC.prepared(reply_idT);
                    break;
                case "C":
                    this.twoPC.commit(reply_idT);
                    c.setPut_answers(true);
                    break;
                case "A":
                    this.twoPC.rollback(reply_idT);
                    c.setPut_answers(false);
                    break;
                default:
                    System.out.println("Invalid state");
            }

            c.setNr_recebido(0);

            this.twoPC.logToString();
            System.out.println("Requested \""+status+"\" for transaction "+reply_idT+
                    " to server(s) "+this.participants.get(reply_idT).toString()+" FOR THE LAST TIME");
        }
        else {
            this.twoPC.rollback(reply_idT);
            c.setPut_answers(false);

            this.twoPC.finished(reply_idT);
            this.twoPC.logToString();

            CompletableFuture<Boolean> cf = this.putCompletableFutures.get(reply_idT);
            if (cf!=null) cf.complete(false);

            else { //significa que essa transação veio de um restart
                Address cliAddress = this.twoPC.logTidCliAddress(reply_idT);
                int cliRequestID = this.twoPC.logTidCliRequestID(reply_idT);
                ms.sendAsync(cliAddress, "put", s.encode(new PutReply(cliRequestID, false)));
            }

            System.out.println("No more tries! Aborting and sending to client put = false");
        }
    }

    private void handle2PC_Yes(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.servers.length && !stop; i++) {
            try {
                if (origin.equals(this.servers[i]) && this.participants.get(reply_idT).contains(origin)
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("A")
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("C")
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("F")) {
                    //se ainda não tiver recebido nenhum pedido de "abort"
                    //ou feito "abort" pelo restart
                    //ou feito "abort"/"commit"/"finished" no repeatProcess

                    stop = true;

                    final Contador c = this.putCompletableFuturesCount.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a YES from "+origin+" for transaction "+reply_idT+
                            " ("+c.getNr_recebido()+"/"+c.getNr_a_receber()+")");

                    if (c.finished()) {
                        this.twoPC.commit(reply_idT);
                        c.setPut_answers(true);
                        c.setNr_recebido(0);

                        this.twoPC.logToString();
                        System.out.println("Requested \"commit\" for transaction " + reply_idT +
                                " to server(s) " + this.participants.get(reply_idT).toString());
                    }
                }
            } catch (NullPointerException ignored){}
        }
    }

    private void handle2PC_No(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.servers.length && !stop; i++) {
            try{
                if (origin.equals(this.servers[i]) && this.participants.get(reply_idT).contains(origin)
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("A")
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("C")
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("F")) {
                    //se ainda não tiver recebido nenhum pedido de "abort"
                    //ou feito "abort" pelo restart
                    //ou feito "abort"/"commit"/"finished" no repeatProcess

                    stop = true;

                    final Contador c = this.putCompletableFuturesCount.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a NO from "+origin+" for transaction "+reply_idT);

                    this.twoPC.rollback(reply_idT);
                    c.setPut_answers(false);
                    c.setNr_recebido(0);

                    this.twoPC.logToString();
                    System.out.println("Requested \"rollback\" for transaction "+reply_idT+
                            " to server(s) "+this.participants.get(reply_idT).toString());
                }
            } catch (NullPointerException ignored){}
        }
    }

    private void handle2PC_Finished(final Address origin, final byte[] bytes) {
        boolean stop = false;
        int reply_idT = this.s.decode(bytes);

        for (int i = 0; i < this.servers.length && !stop; i++) {
            try{
                if (origin.equals(this.servers[i]) && this.participants.get(reply_idT).contains(origin)
                        && !this.twoPC.logTidLastStatus(reply_idT).equals("F")) { //pelo repeatProcess

                    /**testar caso em que o Coordenador reinicia com status="C" ou status="A"*/
                    /*try {
                        System.out.println("Going to sleep with status=\"C\" or status=\"A\"");
                        Thread.sleep(5000);
                        System.out.println("Woke up!!!");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/

                    stop = true;

                    final Contador c = this.putCompletableFuturesCount.get(reply_idT);
                    c.increment_nr_recebido();

                    System.out.println("Recieved a FINISHED from "+origin+" for transaction "+reply_idT+
                            " ("+c.getNr_recebido()+"/"+c.getNr_a_receber()+")");

                    if (c.finished()) {
                        if(this.twoPC.logTidLastStatus(reply_idT).equals("A")){
                            c.setPut_answers(false); //porque restart que não coloca a falso no contador
                        }

                        this.twoPC.finished(reply_idT);

                        CompletableFuture<Boolean> cf = this.putCompletableFutures.get(reply_idT);
                        if (cf!=null) cf.complete(c.getPut_answers());
                        else { //significa que essa transação veio de um restart
                            Address cliAddress = this.twoPC.logTidCliAddress(reply_idT);
                            int cliRequestID = this.twoPC.logTidCliRequestID(reply_idT);
                            ms.sendAsync(cliAddress, "put", s.encode(new PutReply(cliRequestID, c.getPut_answers())));
                        }

                        this.twoPC.logToString();
                        System.out.println("All participants finished 2PC for transaction "+reply_idT+
                                ". Sending to client put = "+c.getPut_answers());
                    }
                }
            } catch (NullPointerException ignored){}
        }
    }
}