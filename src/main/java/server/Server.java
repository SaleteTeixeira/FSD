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

    public static void main(String[] args){
        final Serializer s = Util.getSerializer();
        final ManagedMessagingService ms = NettyMessagingService.builder()
                .withAddress(Address.from(12345))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();
        final Map<Long, byte[]> data = new HashMap<>();
        final Map<Long, Integer> clock = new HashMap<>();

        ms.registerHandler("put", (o,m) -> {
            boolean result;
            final PutServerRequest request = s.decode(m);

            if(acceptInsertion(clock, request.getTransactionID(), request.getValues())){
                for(Map.Entry<Long, byte[]> entry: request.getValues().entrySet()){
                    data.put(entry.getKey(), entry.getValue());
                    clock.put(entry.getKey(), request.getTransactionID());
                }
                result = true;
            }
            else result = false;

            ms.sendAsync(o,"putServer",
                    s.encode(new PutServerReply(request.getTransactionID(), result))
            );
        }, es);

        ms.registerHandler("get", (o,m) -> {
            final GetServerRequest request = s.decode(m);
            System.out.println(request.toString());

            Map<Long, byte[]> tmp = new HashMap<>();
            request.getKeys().forEach(k -> {byte[] v = data.get(k); tmp.put(k,v);});

            ms.sendAsync(o,"getServer",
                    s.encode(new GetServerReply(request.getTransactionID(),tmp))
            );
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static boolean acceptInsertion(Map<Long, Integer> clock, int transactionID, Map<Long, byte[]> values){
        for(Map.Entry<Long, byte[]> entry: values.entrySet()){
            if(clock.containsKey(entry.getKey()))
                if(clock.get(entry.getKey()) >= transactionID) return false;
        }

        return true;
    }
}
