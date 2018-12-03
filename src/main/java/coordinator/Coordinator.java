package coordinator;

import common.*;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class Coordinator {
    public static void main(final String[] args) {
        final var serializer = Util.getSerializer();
        final var messagingService = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:11110"))
                .build();
        final var executorService = Executors.newSingleThreadExecutor();
        final var map = new HashMap<Long, byte[]>();
        final var store = new Store();

        messagingService.registerHandler("put", (origin, bytes) -> {
            final PutRequest request = serializer.decode(bytes);
            System.out.println(request.toString());
            store.put(request.getValues());
            // map.putAll(request.getValues());
            messagingService.sendAsync(origin,
                    "put",
                    serializer.encode(new PutReply(request.getClientID(),
                            request.getTransactionID(),
                            true)));
        }, executorService);

        messagingService.registerHandler("get", (origin, bytes) -> {
            final GetRequest request = serializer.decode(bytes);
            System.out.println(request.toString());
            final var temp = new HashMap<Long, byte[]>();
            //request.getKeys().forEach((k) -> temp.put(k, map.get(k)));
            messagingService.sendAsync(origin,
                    "get",
                    serializer.encode(new GetReply(request.getClientID(),
                            request.getTransactionID(),
                            temp)));
        }, executorService);

        try {
            messagingService.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
