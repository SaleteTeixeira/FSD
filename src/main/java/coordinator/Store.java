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

public class Store {

    public class Contador {
        private int nr_a_receber;
        private int nr_recebido;
        private boolean put_answers;
        private Map<Long, byte[]> get_answers;

        Contador(int nr_a_receber) {
            this.nr_a_receber = nr_a_receber;
            this.nr_recebido = 0;
            this.put_answers = true;
            this.get_answers = new HashMap<>();
        }

        public boolean finished() {
            return this.nr_a_receber == this.nr_recebido;
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

        public Map<Long, byte[]> getGet_answers() {
            return this.get_answers;
        }

        public void setGet_answers(Map<Long, byte[]> get_answers) {
            for (Map.Entry<Long, byte[]> s : get_answers.entrySet()) {
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

        final Map<Address, Map<Long, byte[]>> temp = new HashMap<>();
        for (final Address server : this.servers) {
            temp.put(server, new HashMap<>());
        }
        values.forEach((k, v) -> {
            final Address index = this.servers[(int) (k % (this.servers.length))];
            temp.get(index).put(k, v);
        });

        int count = 0;
        for (final Address address : temp.keySet()) {
            if (temp.get(address).size() > 0) {
                count++;
            }
        }

        if (count == 0) {
            t.complete(true);
            return t;
        }

        this.putCompletableFuturesCount.put(this.transactionID, new Contador(count));

        temp.forEach((k, v) -> {
            if (v.size() > 0) {
                this.ms.sendAsync(k, "put", this.s.encode(new PutRequest(requestID, this.transactionID, temp.get(k))));
            }
        });

        return t;
    }

    public CompletableFuture<Map<Long, byte[]>> get(final int requestID, final Collection<Long> keys) {
        final CompletableFuture<Map<Long, byte[]>> t = new CompletableFuture<>();
        this.transactionID++;
        this.getCompletableFutures.put(this.transactionID, t);
        this.getCompletableFuturesCount.put(this.transactionID, new Contador(keys.size()));

        final Map<Address, Collection<Long>> temp = new HashMap<>();
        for (final Address server : this.servers) {
            temp.put(server, new ArrayList<>());
        }

        keys.forEach(k -> {
            final Address index = this.servers[(int) (k % (this.servers.length))];
            temp.get(index).add(k);
        });

        int count = 0;
        for (final Address address : temp.keySet()) {
            if (temp.get(address).size() > 0) {
                count++;
            }
        }

        if (count == 0) {
            t.complete(new HashMap<>());
            return t;
        }

        this.getCompletableFuturesCount.put(this.transactionID, new Contador(count));

        temp.forEach((k, v) -> {
            if (v.size() > 0) {
                this.ms.sendAsync(k, "get", this.s.encode(new GetRequest(requestID, this.transactionID, temp.get(k))));
            }
        });

        return t;
    }

    private void handlePut(final Address origin, final byte[] bytes) {
        boolean stop = false;

        for (int i = 0; i < servers.length && !stop; i++) {
            if (origin.equals(servers[i])) {
                stop = true;

                final PutReply reply = this.s.decode(bytes);

                Contador c = this.putCompletableFuturesCount.get(reply.getTransactionID());
                c.increment_nr_recebido();
                c.setPut_answers(reply.getValue());

                if (c.finished()) {
                    this.putCompletableFutures.get(reply.getTransactionID()).complete(c.getPut_answers());
                }
            }
        }
    }

    private void handleGet(final Address origin, final byte[] bytes) {
        boolean stop = false;

        for (int i = 0; i < servers.length && !stop; i++) {
            if (origin.equals(servers[i])) {
                stop = true;

                final GetReply reply = this.s.decode(bytes);

                Contador c = this.getCompletableFuturesCount.get(reply.getTransactionID());
                c.increment_nr_recebido();
                c.setGet_answers(reply.getValues());

                if (c.finished()) {
                    this.getCompletableFutures.get(reply.getTransactionID()).complete(c.getGet_answers());
                }
            }
        }
    }
}
