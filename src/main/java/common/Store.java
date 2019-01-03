package common;

import io.atomix.utils.net.Address;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Store {

    CompletableFuture<Boolean> put(Map<Long,byte[]> values);

    CompletableFuture<Map<Long,byte[]>> get(Collection<Long> keys);

    void handleGet(final Address origin, final byte[] bytes);

    void handlePut(final Address origin, final byte[] bytes);
}
