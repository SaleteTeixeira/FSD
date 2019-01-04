package server;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class Server {

    public static void main(final String[] args) {
        final Serializer s = Util.getSerializer();
        final ManagedMessagingService ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:" + args[0]))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();
        final Address coordAdd = Address.from("localhost:22222");
        TwoPhaseCommit twoPC = new TwoPhaseCommit(s, ms, es, coordAdd, "LOG - server"+args[0],args[0]);
        AtomicReference<Store> store = new AtomicReference<>();

        ms.registerHandler("get", (o, m) -> {
            if (o.equals(coordAdd)){
                final GetRequest request = s.decode(m);
                System.out.println("\n"+request.toString());

                final Map<Long, byte[]> result = store.get().get(request.getKeys());

                GetReply answer = new GetReply(request.getReq_tran_ID(), result);
                ms.sendAsync(o, "get", s.encode(answer));
                System.out.println("Answer: " + answer.toString());
            }
        }, es);


        //usa log
        ms.registerHandler("put", (o, m) -> {
            if (o.equals(coordAdd)) {
                final PutRequest request = s.decode(m);
                System.out.println("\n" + request.toString());

                store.get().put(request.getValues(), request.getReq_tran_ID());
                store.get().printStore();

                twoPC.writeLog(request.getReq_tran_ID(),request.getValues());
                twoPC.logToString();

                PutReply answer = new PutReply(request.getReq_tran_ID(), true);
                ms.sendAsync(o, "put", s.encode(answer));
                System.out.println("Answer: " + answer.toString());
            }
        }, es);

        ms.registerHandler("restartStore", (o, m) -> {
            if (o.equals(Address.from("localhost:"+args[0]))) {
                store.set(twoPC.restartStore());
                store.get().printStore();
            }
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        twoPC.logToString();
        System.out.println("⇢ Restart server " + args[0]);
        store.set(twoPC.restartStore());
        store.get().printStore();
        twoPC.restart();
        twoPC.logToString();
    }
}