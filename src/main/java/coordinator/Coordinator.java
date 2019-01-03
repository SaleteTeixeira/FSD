package coordinator;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

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

        ms.registerHandler("get", (o,m) -> {
            final GetRequest request = s.decode(m);

            System.out.println(request.toString());

            store.get(request.getKeys()).thenAccept((map) -> {
                ms.sendAsync(o, "get", s.encode(new GetReply(request.getReq_tran_ID(), map)));
            });
        }, es);

        ms.registerHandler("put", (o,m) -> {
            final PutRequest request = s.decode(m);

            System.out.println(request.toString());

            store.put(o, request.getReq_tran_ID(), request.getValues()).thenAccept((bool) -> {
                ms.sendAsync(o, "put", s.encode(new PutReply(request.getReq_tran_ID(), bool)));
            });
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
