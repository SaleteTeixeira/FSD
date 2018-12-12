package coordinator;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.storage.journal.SegmentedJournal;
import io.atomix.storage.journal.SegmentedJournalWriter;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class TwoPhaseCommit implements common.TwoPhaseCommit {
    private Serializer s, sl;
    private ManagedMessagingService ms;
    private ExecutorService es;
    private SegmentedJournal<Object> journal;
    private SegmentedJournalWriter<Object> writer;

    public TwoPhaseCommit(Serializer s, ManagedMessagingService ms, ExecutorService es){
        this.s = s;
        this.ms = ms;
        this.es = es;
        this.sl = Serializer.builder().withTypes(CoordinatorLog.class).build();
        this.journal = SegmentedJournal.builder().withName("coordinator").withSerializer(sl).build();
        this.writer = this.journal.writer();
    }

    @Override
    public CompletableFuture<Boolean> start(final int transactionID, final Collection<Address> participants) {
        Map<Address, Integer> respCommit = new HashMap<>();
        Map<Address, Integer> respPrep = new HashMap<>();

        /*
          FALTA ESCREVER TUDO NO LOG, FALTA VEFICAR EM QUE PASSO ESTÁ O LOG ANTES DE SE FAZER AS COISAS E
          FALTA ALGO A CONTAR O TEMPO O O NR DE PREPARED ENVIADOS PARA FAZER ABORT CASO ALGUM PARTICIPANTE NÃO RESPONDA
        */

        for(Address add: participants) {
            this.ms.sendAsync(add,"prepared", this.s.encode("Prepared?"));
        }

        while(respPrep.size() < participants.size()){
            //não tenho a certeza se se deve verificar se o prepared é para esta transação ou não
            this.ms.registerHandler("prepared", (o, m) ->{
                String line = this.s.decode(m);

                if(line.equals("Abort")){
                    for(Address add: participants){
                        this.ms.sendAsync(add, "commit", this.s.encode("Abort"));
                    }
                    return CompletableFuture.completedFuture(false);
                }

                respPrep.put(o, 1);
            }, this.es);
        }

        for(Address add: participants){
            this.ms.sendAsync(add, "commit", this.s.encode("Commit"));
        }

        while(respCommit.size() < participants.size()){
            //não tenho a certeza se se deve verificar se o commit é para esta transação ou não
            this.ms.registerHandler("commit", (o, m) -> {
                respCommit.put(o, 1);
            }, this.es);
        }
        return CompletableFuture.completedFuture(true);
    }
}
