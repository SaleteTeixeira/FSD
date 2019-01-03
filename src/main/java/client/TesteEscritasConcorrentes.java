package client;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TesteEscritasConcorrentes {
    public static void main(final String[] args) {
        final Serializer s = Util.getSerializer();
        final ManagedMessagingService ms = NettyMessagingService.builder().withAddress(Address.from("localhost:22222")).build();
        final ExecutorService es = Executors.newSingleThreadExecutor();
        Map<Long, byte[]> t1 = new HashMap<>();
        Map<Long, byte[]> t2 = new HashMap<>();
        Collection<Long> keys = new ArrayList<>();

        ms.registerHandler("put", (o, m) -> {
            final PutReply reply = s.decode(m);

            System.out.println("Recebido put: "+reply.toString());
        }, es);

        ms.registerHandler("get", (o, m) -> {
            final GetReply reply = s.decode(m);

            System.out.println("Recebido get: "+reply.toString());
        }, es);

        ms.start();

        String ka1 = "a1";
        String ka2 = "a2";
        String kb1 = "b1";
        String kb2 = "b2";
        String kc1 = "c1";
        String kd2 = "d2";

        t1.put((long) 1, ka1.getBytes());
        t2.put((long) 1, ka2.getBytes());
        t1.put((long) 2, kb1.getBytes());
        t2.put((long) 2, kb2.getBytes());
        t1.put((long) 3, kc1.getBytes());
        t2.put((long) 4, kd2.getBytes());


        ms.sendAsync(Address.from(12345), "put", s.encode(new PutRequest(1, t1)));

        /** para receber primeiro a t1 **/
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ms.sendAsync(Address.from(12345), "put", s.encode(new PutRequest(0, t2)));

        keys.add((long) 1);
        keys.add((long) 2);
        keys.add((long) 3);
        keys.add((long) 4);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ms.sendAsync(Address.from(12345), "get", s.encode(new GetRequest( 2, keys)));
    }
}