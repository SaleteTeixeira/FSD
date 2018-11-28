package client;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

class Store {
    private final int clientID;
    private int transactionID = 0;

    private final Serializer serializer;
    private final ManagedMessagingService messagingService;

    private final Map<Integer, CompletableFuture<Boolean>> putCompletableFutures;
    private final Map<Integer, CompletableFuture<Map<Long, byte[]>>> getCompletableFutures;
    Store(final int clientID) {
        this.clientID = clientID;
        this.serializer = Util.getSerializer();
        this.messagingService = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:22222"))
                .build();
        final var executorService = Executors.newSingleThreadExecutor();
        this.messagingService.registerHandler("put", this::handlePut, executorService);
        this.messagingService.registerHandler("get", this::handleGet, executorService);
        try {
            this.messagingService.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }

        this.putCompletableFutures = new HashMap<>();
        this.getCompletableFutures = new HashMap<>();
    }

    CompletableFuture<Boolean> put(final Map<Long, byte[]> values) {
        final var t = new CompletableFuture<Boolean>();
        this.putCompletableFutures.put(this.transactionID, t);
        this.messagingService.sendAsync(Address.from("localhost:11110"),
                "put",
                this.serializer.encode(new PutRequest(this.clientID, this.transactionID++, values)));
        return t;
    }

    CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        final var t = new CompletableFuture<Map<Long, byte[]>>();
        this.getCompletableFutures.put(this.transactionID, t);
        this.messagingService.sendAsync(Address.from("localhost:11110"),
                "get",
                this.serializer.encode(new GetRequest(this.clientID, this.transactionID++, keys)));
        return t;
    }

    private void handlePut(final Address origin, final byte[] bytes) {
        final PutReply reply = this.serializer.decode(bytes);
        this.putCompletableFutures.get(reply.getTransactionID()).complete(reply.getValue());
    }

    private void handleGet(final Address origin, final byte[] bytes) {
        final GetReply reply = this.serializer.decode(bytes);
        this.getCompletableFutures.get(reply.getTransactionID()).complete(reply.getKeys());
    }
}
