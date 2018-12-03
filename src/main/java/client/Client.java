package client;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class Client {
    public static void main(final String[] args) {
        final var numOps = 100; // Total number of gets + puts
        final var maxNumKeys = 20; // Maximum number of keys in a single request
        //final var keyUpperBound = Integer.MAX_VALUE; // Upper bound of a given key
        final var keyUpperBound = 10; // Upper bound of a given key
        final var valueLength = 20; // Length of each byte[] in put request

        final var store = new Store(Integer.parseInt(args[0]));
        final var random = new Random();
        for (var i = 0; i < numOps; i++) {
            final var op = random.nextInt(2);
            final var numKeys = random.nextInt(maxNumKeys + 1);
            final var keys = new ArrayList<Long>();
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
                    final var values = new HashMap<Long, byte[]>();
                    keys.forEach(k -> {
                        final var temp = new byte[valueLength];
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
    }
}
