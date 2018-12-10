package common;

import io.atomix.utils.net.Address;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface TwoPhaseCommit {

    CompletableFuture<Boolean> start(final int transactionID, final Collection<Address> participants);
}
