package client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

class Store implements common.Store {
    private int requestID = 0;
    private final Serializer s;
    private final ManagedMessagingService ms;
    private final Map<Integer, CompletableFuture<Boolean>> putCompletableFutures;
    private final Map<Integer, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;

    public Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:" + Integer.getInteger("port", 22220)))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();

        this.ms.registerHandler("put", this::handlePut, es);
        this.ms.registerHandler("get", this::handleGet, es);

        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }

        this.putCompletableFutures = new HashMap<>();
        this.getCompletableFutures = new HashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> put(final Map<Long, byte[]> values) {
        final CompletableFuture<Boolean> t = new CompletableFuture<>();
        this.putCompletableFutures.put(this.requestID, t);
        this.ms.sendAsync(Util.getCoordinator(),
                "put",
                this.s.encode(new PutRequest(this.requestID++, null, values)));
        return t;
    }

    @Override
    public CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        final CompletableFuture<Map<Long, byte[]>> t = new CompletableFuture<Map<Long, byte[]>>();
        this.getCompletableFutures.put(this.requestID, t);
        this.ms.sendAsync(Util.getCoordinator(),
                "get",
                this.s.encode(new GetRequest(this.requestID++, null, keys)));
        return t;
    }

    private void handlePut(final Address origin, final byte[] bytes) {
        if (origin.equals(Util.getCoordinator())) {
            final PutReply reply = this.s.decode(bytes);
            this.putCompletableFutures.get(reply.getRequestID()).completedFuture(reply.getValue());
        }
    }

    private void handleGet(final Address origin, final byte[] bytes) {
        if (origin.equals(Util.getCoordinator())) {
            final GetReply reply = this.s.decode(bytes);
            this.getCompletableFutures.get(reply.getRequestID()).completedFuture(reply.getValues());
        }
    }
}
