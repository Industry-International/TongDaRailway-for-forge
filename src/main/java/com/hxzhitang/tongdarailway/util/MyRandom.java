package com.hxzhitang.tongdarailway.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MyRandom {

    /**
     * Generate points in [0, range] range with minimum distance from edges
     *
     * @param seed  random seed
     * @param range coordinate range
     * @return int array of (x, z) coordinates
     */
    public static int[] generatePoints(long seed, int range) {
        Random random = new Random(seed);

        int quart = range / 4;

        // Generate first point
        int x1 = random.nextInt(quart, range - quart);
        int z1 = random.nextInt(quart, range - quart);

        return new int[]{x1, z1};
    }

    /**
     * Get random value from Map based on seed
     * @param map map object
     * @param seed random seed
     * @param <K> key type
     * @param <V> value type
     * @return random value, or null if map is empty
     */
    public static <K, V> V getRandomValueFromMap(Map<K, V> map, long seed) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Random random = new Random(seed);
        List<V> values = new ArrayList<>(map.values());
        int randomIndex = random.nextInt(values.size());

        return values.get(randomIndex);
    }
}
