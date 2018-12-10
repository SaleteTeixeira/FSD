package coordinator;

import io.atomix.utils.net.Address;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class TwoPhaseCommit implements common.TwoPhaseCommit {
    @Override
    public CompletableFuture<Boolean> start(final int transactionID, final Collection<Address> participants) {
        return CompletableFuture.completedFuture(true);
    }
}
