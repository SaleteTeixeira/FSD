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
        TwoPhaseCommit twoPC = new TwoPhaseCommit(s, ms, es, coordAdd, "LOG - server"+args[0]);
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

                twoPC.start(request.getReq_tran_ID(),request.getValues());
                twoPC.logToString();

                PutReply answer = new PutReply(request.getReq_tran_ID(), true);
                ms.sendAsync(o, "put", s.encode(answer));
                System.out.println("Answer: " + answer.toString());
            }
        }, es);


        ms.registerHandler("prepared", (o,m)->{
            if (o.equals(coordAdd)){
                int reply_idT = s.decode(m);

                /**testar caso em que o Servidor reinicia com status="", com timeCounter=15s
                 * OU testar abort do repeatProcess para timeCounter=1ms**/
                /*try {
                    System.out.println("Going to sleep with status=\""+twoPC.logTidLastStatus(reply_idT)+"\" for transaction "+reply_idT);
                    Thread.sleep(10000);
                    System.out.println("Woke up!!!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/

                twoPC.prepared(reply_idT, "yes");
                twoPC.logToString();
                System.out.println("Server "+args[0]+" prepared for transaction "+ reply_idT);
            }
        }, es);


        ms.registerHandler("commit", (o,m)->{
            if (o.equals(coordAdd)) {
                int reply_idT = s.decode(m);

                /**testar caso em que o Servidor reinicia com status=P, com timeCounter=15s
                 * OU testar abort do repeatProcess para timeCounter=1ms**/
                /*try {
                    System.out.println("Going to sleep with status=\""+twoPC.logTidLastStatus(reply_idT)+"\" for transaction "+reply_idT);
                    Thread.sleep(10000);
                    System.out.println("Woke up!!!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/

                twoPC.commit(reply_idT);
                twoPC.logToString();
                System.out.println("Transaction " + reply_idT + " commited by server " + args[0]);
            }
        }, es);


        ms.registerHandler("rollback", (o,m)->{
            if (o.equals(coordAdd)) {
                int reply_idT = s.decode(m);

                /**testar caso em que o Servidor reinicia com status=P, com timeCounter=15s
                 * OU testar abort do repeatProcess para timeCounter=1ms**/
                /*try {
                    System.out.println("Going to sleep with status=\""+twoPC.logTidLastStatus(reply_idT)+"\" for transaction "+reply_idT);
                    Thread.sleep(10000);
                    System.out.println("Woke up!!!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/

                twoPC.rollback(reply_idT);

                store.set(twoPC.restartStore());
                store.get().printStore();

                twoPC.logToString();
                System.out.println("Transaction " + reply_idT + " aborted by server " + args[0]);
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