package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Store {
    private final Serializer s;
    private final ManagedMessagingService ms;
    private final ExecutorService es;
    private Address[] servers;

    private final Map<AtomicInteger, CompletableFuture<Boolean>> putCompletableFutures;
    private final Map<AtomicInteger, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;
    private final Map<AtomicInteger, Integer> getCompletableFuturesCount;

    private AtomicInteger transactionID = null;

    public Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:22222"))
                .build();
        this.es = Executors.newSingleThreadExecutor();

        this.ms.registerHandler("put", this::handlePut, es);
        this.ms.registerHandler("get", this::handleGet, es);

        this.servers = new Address[]{Address.from("localhost:12345"), Address.from("localhost:12346"), Address.from("localhost:12347")};
        this.transactionID.set(-1);

        this.putCompletableFutures = new HashMap<>();
        this.getCompletableFutures = new HashMap<>();
        this.getCompletableFuturesCount = new HashMap<>();

        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public CompletableFuture<Boolean> put(final int requestID, final Map<Long, byte[]> values) {
        final var t = new CompletableFuture<Boolean>();
        this.transactionID.getAndIncrement();
        this.putCompletableFutures.put(this.transactionID, t);

        for(Long key : values.keySet()){
            int serv_id = (int) (key%(servers.length));

            ms.sendAsync(servers[serv_id],
                "put",
                s.encode(new PutRequest(requestID, this.transactionID, values))
            );
        }

        return t;
    }

    public CompletableFuture<Map<Long, byte[]>> get(final int requestID, final Collection<Long> keys) {
        final var t = new CompletableFuture<Map<Long, byte[]>>();
        this.transactionID.getAndIncrement();
        this.getCompletableFutures.put(this.transactionID, t);

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
                this.putCompletableFutures.get(reply.getTransactionID()).complete(reply.getValue());
            }
        }
    }

    private void handleGet(final Address origin, final byte[] bytes) {

        //DUVIDA: ele recebe várias respostas ao GET porque manda get para os servidores que tiverem as chaves
        //mas a resposta é só um CompletableFuture<Map<Long, byte[]>>, não uma coleção deles

        boolean stop=false;

        for(int i=0; i<servers.length && !stop; i++){
            if(origin.equals(servers[i])) {
                stop=true;

                final GetReply reply = this.s.decode(bytes);
                this.getCompletableFutures.get(reply.getTransactionID()).complete(reply.getValues());
            }
        }
    }
}
