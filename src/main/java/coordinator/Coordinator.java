package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator {
    public static void main(final String[] args) {
        final Serializer s = Util.getSerializer();
        final ManagedMessagingService ms = NettyMessagingService.builder()
                                                                .withAddress(Address.from("localhost:11110"))
                                                                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();
        final Store store = new Store();

        ms.registerHandler("put", (o,m) -> {
            final PutRequest request = s.decode(m);
            CompletableFuture<Boolean> r = store.put(request.getRequestID(), request.getValues());

            //DUVIDA: pode ser assim? ou usar o thenCompose???
            try {
                ms.sendAsync(o,
                    "put",
                    s.encode(new PutReply(request.getRequestID(), null, r.get()))
                );
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }, es);

        ms.registerHandler("get", (o,m) -> {
            final GetRequest request = s.decode(m);

            CompletableFuture<Map<Long, byte[]>> r = store.get(request.getRequestID(), request.getKeys());

            //DUVIDA: pode ser assim? ou usar o thenCompose???
             try {
                ms.sendAsync(o,
                    "get",
                    s.encode(new GetReply(request.getRequestID(), null, r.get()))
                );
             } catch (InterruptedException | ExecutionException e) {
                 e.printStackTrace();
             }
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
