package server;

import common.*;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Collection;
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
            final PutRequest request = s.decode(m);

            for(Map.Entry<Long, byte[]> entry: request.getValues().entrySet()){
                if(change_value(clock, entry.getKey(), request.getRequestID())){
                    data.put(entry.getKey(), entry.getValue());
                    clock.put(entry.getKey(), request.getRequestID());
                }
            }

            ms.sendAsync(o,"putServer",
                    s.encode(new PutReply(request.getRequestID(), true))
            );
        }, es);

        ms.registerHandler("get", (o,m) -> {
            final GetRequest request = s.decode(m);

            Map<Long, byte[]> result = insert(request.getKeys(), data);

            ms.sendAsync(o,"getServer",
                    s.encode(new GetReply(request.getRequestID(),result))
            );
        }, es);

        try {
            ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static boolean change_value(Map<Long, Integer> clock, long key, int transactionID){
        if(clock.containsKey(key))
            if(clock.get(key) >= transactionID) return false;

        return true;
    }

    public static Map<Long, byte[]> insert(Collection<Long> keys, Map<Long, byte[]> data){
        Map<Long, byte[]> tmp = new HashMap<>();
        keys.forEach(k -> {byte[] v = data.get(k); tmp.put(k,v);});

        return tmp;
    }
}
