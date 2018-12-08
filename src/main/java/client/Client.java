package client;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Client {
    public static void main(final String[] args) {
        final int numOps = 10; // Total number of gets + puts
        final int maxNumKeys = 20; // Maximum number of keys in a single request
        //final int keyUpperBound = Integer.MAX_VALUE; // Upper bound of a given key
        final int keyUpperBound = 10; // Upper bound of a given key
        final int valueLength = 20; // Length of each byte[] in put request

        final Store store = new Store();
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
                    store.get(keys).thenAccept((map) -> {
                        System.out.println("Resposta a um get: " +map.toString());
                    });

                    break;
                case 1: // Put
                    final Map<Long, byte[]> values = new HashMap<>();

                    keys.forEach(k -> {
                        final byte[] temp = new byte[valueLength];
                        for (int j = 0; j < valueLength; j++) {
                            temp[j] = (byte) random.nextInt(Byte.MAX_VALUE + 1);
                        }
                        values.put(k, temp);

                        System.out.println(k +" "+temp);
                    });

                    store.put(values).thenAccept((bool) -> {
                        System.out.println("Resposta a um put: " + bool);
                    });

                    break;
                default:
                    System.exit(1);
                    break;
            }
        }

        try {
            TimeUnit.MINUTES.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.exit(0);
    }
}
