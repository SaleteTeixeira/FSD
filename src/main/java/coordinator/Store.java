package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.security.Key;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Store {

    public class Contador{
        private int nr_a_receber;
        private int nr_recebido;
        private boolean put_answers;
        private Map<Long, byte[]> get_answers;

        Contador(int nr_a_r, int nr_r){
            this.nr_a_receber = nr_a_r;
            this.nr_recebido = nr_r;
            this.put_answers = true;
            this.get_answers = new HashMap<>();
        }

        public boolean finished(){
            return this.nr_a_receber==this.nr_recebido;
        }
        public void increment_nr_recebido() {
            this.nr_recebido++;
        }
        public boolean getPut_answers() {
            return this.put_answers;
        }
        public void setPut_answers(boolean put_answers) {
            this.put_answers = put_answers;
        }
        public Map<Long,byte[]> getGet_answers(){
            return this.get_answers;
        }
        public void setGet_answers(Map<Long, byte[]> get_answers) {
            for(Map.Entry<Long, byte[]> s : get_answers.entrySet()){
                this.get_answers.put(s.getKey(), s.getValue());
            }
        }
    }

    private final Serializer s;
    private final ManagedMessagingService ms;
    private final ExecutorService es;
    private Address[] servers;

    private final Map<Integer, CompletableFuture<Boolean>> putCompletableFutures;
    private final Map<Integer, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;
    private final Map<Integer, Contador> putCompletableFuturesCount;
    private final Map<Integer, Contador> getCompletableFuturesCount;

    private int transactionID;

    public Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:22222"))
                .build();
        this.es = Executors.newSingleThreadExecutor();

        this.ms.registerHandler("put", this::handlePut, es);
        this.ms.registerHandler("get", this::handleGet, es);

        this.servers = new Address[]{Address.from("localhost:12345"), Address.from("localhost:12346"), Address.from("localhost:12347")};
        this.transactionID = -1;

        this.putCompletableFutures = new HashMap<>();
        this.getCompletableFutures = new HashMap<>();
        this.putCompletableFuturesCount = new HashMap<>();
        this.getCompletableFuturesCount = new HashMap<>();

        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public CompletableFuture<Boolean> put(final int requestID, final Map<Long, byte[]> values) {
        final CompletableFuture<Boolean> t = new CompletableFuture<>();
        this.transactionID++;
        this.putCompletableFutures.put(this.transactionID, t);
        this.putCompletableFuturesCount.put(this.transactionID, new Contador(values.keySet().size(), 0));

        for(Long key : values.keySet()) {
            int serv_id = (int) (key % (servers.length));

            ms.sendAsync(servers[serv_id],
                    "put",
                    s.encode(new PutRequest(requestID, this.transactionID, values))
            );
        }

        return t;
    }

    public CompletableFuture<Map<Long, byte[]>> get(final int requestID, final Collection<Long> keys) {
        final CompletableFuture<Map<Long, byte[]>> t = new CompletableFuture<>();
        this.transactionID++;
        this.getCompletableFutures.put(this.transactionID, t);
        this.getCompletableFuturesCount.put(this.transactionID, new Contador(keys.size(), 0));

        for(Long key : keys){
            int serv_id = (int) (key%(servers.length));

            ms.sendAsync(servers[serv_id],
                    "get",
                    s.encode(new GetRequest(requestID, this.transactionID, keys))
            );
        }
        return t;
    }

    private void handlePut(final Address origin, final byte[] bytes) {
        boolean stop=false;

        for(int i=0; i<servers.length && !stop; i++){
            if(origin.equals(servers[i])) {
                stop=true;

                final PutReply reply = this.s.decode(bytes);

                Contador c = this.putCompletableFuturesCount.get(reply.getTransactionID());
                c.increment_nr_recebido();
                c.setPut_answers(reply.getValue());

                if(c.finished()){
                    this.putCompletableFutures.get(reply.getTransactionID()).complete(c.getPut_answers());
                }
            }
        }
    }

    private void handleGet(final Address origin, final byte[] bytes) {
        boolean stop=false;

        for(int i=0; i<servers.length && !stop; i++){
            if(origin.equals(servers[i])) {
                stop=true;

                final GetReply reply = this.s.decode(bytes);

                Contador c = this.getCompletableFuturesCount.get(reply.getTransactionID());
                c.increment_nr_recebido();
                c.setGet_answers(reply.getValues());

                if(c.finished()) {
                    this.getCompletableFutures.get(reply.getTransactionID()).complete(c.getGet_answers());
                }
            }
        }
    }
}
