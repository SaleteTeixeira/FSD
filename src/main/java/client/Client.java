package client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

public class Client {

    public static void main(final String[] args) {
        final var store = new Store(Integer.parseInt(args[0]));
        final var values = new HashMap<Long, byte[]>();

        for (long i = 0; i < 10; i++) {
            values.put(i, ("Hello" + i).getBytes());
        }

        try {
            System.out.println(store.put(values).get());
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        final var keys = Arrays.asList(1L, 2L, 3L, 4L, 5L);

        try {
            System.out.println(store.get(keys).get());
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }
}
