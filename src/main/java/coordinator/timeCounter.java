package coordinator;

import common.Util;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

public class timeCounter implements Runnable {

    private final Serializer s = Util.getSerializer();
    private final ManagedMessagingService ms;
    private final Address coordAdd = Address.from("localhost:22222");
    private final int tID;
    private final String status;

    timeCounter(int tID, String status){
        this.tID = tID;

        if(status.equals("")) this.status = " ";
        else this.status = status;

        char c = this.status.charAt(0);
        int s = (int) c;

        this.ms = NettyMessagingService.builder()
                .withAddress(Address.from("localhost:3"+this.tID+s))
                .build();

        this.ms.start();
    }

    @Override
    public void run() {
        try {
            //Thread.sleep(15000); //testar o restart dos servers (para o coordenador não fazer abort)
            Thread.sleep(1); //forçar repetição
            this.ms.sendAsync(this.coordAdd, "time", this.s.encode(this.status+" "+this.tID));
            this.ms.stop();
        } catch (InterruptedException ignored) {}
    }
}
