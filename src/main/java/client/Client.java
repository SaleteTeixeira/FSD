package client;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class Client {
    public static void main(final String[] args) {
        final int numOps = 100; // Total number of gets + puts
        final int maxNumKeys = 20; // Maximum number of keys in a single request
        //final var keyUpperBound = Integer.MAX_VALUE; // Upper bound of a given key
        final int keyUpperBound = 10; // Upper bound of a given key
        final int valueLength = 20; // Length of each byte[] in put request

        final Store store = new Store(Integer.parseInt(args[0]));
        final Random random = new Random();

        for (int i = 0; i < numOps; i++) {

            final int op = random.nextInt(2);
            final int numKeys = random.nextInt(maxNumKeys + 1);
            final Collection<Long> keys = new ArrayList<>();

            for (int j = 0; j < numKeys; j++) {
                keys.add((long) random.nextInt(keyUpperBound));
            }

            switch (op) {
                case 0: // Get
                    try {
                        System.out.println(store.get(keys).get());
                    } catch (final InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    break;
                case 1: // Put
                    final Map<Long, byte[]> values = new HashMap<>();

                    keys.forEach(k -> {
                        final byte[] temp = new byte[valueLength];
                        for (int j = 0; j < valueLength; j++) {
                            temp[j] = (byte) random.nextInt(Byte.MAX_VALUE + 1);
                        }
                        values.put(k, temp);
                    });

                    try {
                        System.out.println(store.put(values).get());
                    } catch (final InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    break;
                default:
                    System.exit(1);
                    break;
            }
        }
        System.exit(0);
    }
}
