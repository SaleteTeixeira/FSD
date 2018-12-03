package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingService;
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
            System.out.println(request.toString());
            CompletableFuture<Boolean> result = store.put(request.getValues());
            ms.sendAsync(o,"put",
                    s.encode(new PutReply(request.getClientID(),
                            request.getTransactionID(),
                            //result))      //não me lembro como se faz, porque no PutReply está boolena e o result é um CompletableFuture
            );
        }, es);

        ms.registerHandler("get", (o,m) -> {
            final GetRequest request = s.decode(m);
            System.out.println(request.toString());

            CompletableFuture<Map<Long, byte[]>> result = store.get(request.getKeys());
            ms.sendAsync(o,"get",
                    s.encode(new GetReply(request.getClientID(),
                            request.getTransactionID(),
                            //result))      //não me lembro como se faz, porque no GetReply está boolena e o result é um CompletableFuture
            );
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
