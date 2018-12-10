package server;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static void main(final String[] args) {
        final Serializer s = Util.getSerializer();
        final ManagedMessagingService ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:" + args[0]))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();

        final Store store = new Store();

        ms.registerHandler("put", (o, m) -> {
            final PutRequest request = s.decode(m);
            store.put(request.getValues(), request.getTransactionID());

            System.out.print("Recebi um put: ");
            request.getValues().forEach((k, v) -> {
                System.out.print(k + "=" + (new String(v)) + ' ');
            });
            System.out.println();

            ms.sendAsync(o, "put", s.encode(new PutReply(request.getRequestID(), request.getTransactionID(), true)));
        }, es);

        ms.registerHandler("get", (o, m) -> {
            final GetRequest request = s.decode(m);

            System.out.print("Recebi um get: ");
            request.getKeys().forEach(k -> {
                System.out.print(k + " ");
            });
            System.out.println();

            final Map<Long, byte[]> result = store.get(request.getKeys());

            System.out.print("Vou responder: ");
            result.forEach((k, v) -> {
                if (v != null) {
                    System.out.print(k + "=" + (new String(v)) + ' ');
                } else {
                    System.out.print(k + "=null ");
                }
            });
            System.out.println();

            ms.sendAsync(o, "get", s.encode(new GetReply(request.getRequestID(), request.getTransactionID(), result)));
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
