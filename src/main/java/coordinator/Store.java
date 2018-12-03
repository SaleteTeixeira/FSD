package coordinator;

import common.GetServerReply;
import common.PutServerReply;
import common.PutServerRequest;
import common.Util;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Store implements common.Store {
    private final Serializer s;
    private final ManagedMessagingService ms;
    private final ExecutorService es;
    private Address[] servers;
    private int transactionID;

    Store() {
        this.s = Util.getSerializer();
        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:22222"))
                .build();
        final ExecutorService es = Executors.newSingleThreadExecutor();
        this.servers = new Address[]{Address.from(12345), Address.from(12346), Address.from(12347)};
        this.transactionID = 0;
        this.es = Executors.newSingleThreadExecutor();
        try {
            this.ms.start().get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public CompletableFuture<Boolean> put(final Map<Long, byte[]> values) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        //falta enviar um PutServerRequest para cada um dos servidores necessários

        //nao sei se isto está bem, falta retirar o valor do result, temos que juntar o resultado de todas as mensagens recebidas
        this.ms.registerHandler("putServer", this::handlePut, this.es);

        this.transactionID++;

        return result;
    }

    @Override
    public CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        CompletableFuture<Map<Long, byte[]>> values = new CompletableFuture<>();

        //falta enviar um GetServerRequest para cada um dos servidores necessários

        //nao sei se isto está bem, falta retirar o valor do values, temos que juntar o resultado de todas as mensagens recebidas
        this.ms.registerHandler("getServer", this::handleGet, this.es);

        this.transactionID++;

        return values;
    }

    private byte[] handlePut(Address address, byte[] bytes) {
        final PutServerReply reply = s.decode(bytes);
        //falta completar
    }

    private byte[] handleGet(Address address, byte[] bytes) {
        final GetServerReply reply = s.decode(bytes);
        //falta completar
    }
}
