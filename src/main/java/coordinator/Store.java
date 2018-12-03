package coordinator;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Store implements common.Store {

    Store() {

    }

    @Override
    public CompletableFuture<Boolean> put(final Map<Long, byte[]> values) {
        return null;
    }

    @Override
    public CompletableFuture<Map<Long, byte[]>> get(final Collection<Long> keys) {
        return null;
    }
}
