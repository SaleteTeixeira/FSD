package client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

class Store implements common.Store {
    private final Serializer s;
    private final ManagedMessagingService ms;

    private int requestID = 0;
    private final Map<Integer, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;
    private final Map<Integer, CompletableFuture<Boolean>> putCompletableFutures;

    Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:" + Integer.getInteger("port", 22220)))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();

        this.ms.registerHandler("get", this::handleGet, es);
        this.ms.registerHandler("put", this::handlePut, es);

        this.getCompletableFutures = new HashMap<>();
        this.putCompletableFutures = new HashMap<>();

        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        final CompletableFuture<Map<Long, byte[]>> t = new CompletableFuture<>();
        this.getCompletableFutures.put(this.requestID, t);

        this.ms.sendAsync(Util.getCoordinator(),
                "get",
                this.s.encode(new GetRequest(this.requestID++, keys))
        );

        return t;
    }

    public CompletableFuture<Boolean> put(final Map<Long, byte[]> values) {
        final CompletableFuture<Boolean> t = new CompletableFuture<>();
        this.putCompletableFutures.put(this.requestID, t);

        this.ms.sendAsync(Util.getCoordinator(),
                "put",
                this.s.encode(new PutRequest(this.requestID++, values))
        );

        return t;
    }

    public void handleGet(final Address origin, final byte[] bytes) {
        if (origin.equals(Util.getCoordinator())) {
            final GetReply reply = this.s.decode(bytes);
            this.getCompletableFutures.get(reply.getReq_tran_ID()).complete(reply.getValues());
        }
    }

    public void handlePut(final Address origin, final byte[] bytes) {
        if (origin.equals(Util.getCoordinator())) {
            final PutReply reply = this.s.decode(bytes);
            this.putCompletableFutures.get(reply.getReq_tran_ID()).complete(reply.getValue());
        }
    }
}
