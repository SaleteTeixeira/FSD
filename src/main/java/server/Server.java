package server;

import common.*;
import coordinator.CoordinatorLog;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.storage.journal.SegmentedJournal;
import io.atomix.storage.journal.SegmentedJournalWriter;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

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

        Serializer sl = Serializer.builder().withTypes(ServerLog.class).build();
        SegmentedJournal<Object> journal = SegmentedJournal.builder().withName("server-"+args[0]).withSerializer(sl).build();
        SegmentedJournalWriter<Object> writer = journal.writer();

        ms.registerHandler("put", (o, m) -> {
            final PutRequest request = s.decode(m);
            store.put(request.getValues(), request.getTransactionID());

            System.out.println(request.toString());

            ms.sendAsync(o, "put", s.encode(new PutReply(request.getRequestID(), request.getTransactionID(), true)));
        }, es);

        ms.registerHandler("get", (o, m) -> {
            final GetRequest request = s.decode(m);

            System.out.println(request.toString());

            final Map<Long, byte[]> result = store.get(request.getKeys());

            System.out.println("Vou responder: " + Util.valuesToString(result));

            ms.sendAsync(o, "get", s.encode(new GetReply(request.getRequestID(), request.getTransactionID(), result)));
        }, es);

        ms.registerHandler("prepared", (o, m) -> {
            //meter valores no log (?)
            //meter P no log
            ms.sendAsync(o, "prepared", s.encode("Ok"));
        }, es);

        ms.registerHandler("commit", (o, m) -> {
            String line = s.decode(m);

            if(line.equals("Commit")){
                //meter C no log
                ms.sendAsync(o, "commit", s.encode("Ok"));
            }
            else{
                //cancelar: meter A no log (?) igualar map aos valores do log (?) -- estou confusa :(
            }

        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
