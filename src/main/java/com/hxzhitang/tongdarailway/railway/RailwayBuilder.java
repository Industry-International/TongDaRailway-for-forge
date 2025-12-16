package com.hxzhitang.tongdarailway.railway;

import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.util.ModSaveData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class RailwayBuilder {
    private static RailwayBuilder instance;
    private static long seed;

    private final Map<RegionPos, Future<?>> regionFutures = new ConcurrentHashMap<>();
    public final Map<RegionPos, RailwayMap> regionRailways = new ConcurrentHashMap<>();
    public final Map<RegionPos, int[][]> regionHeightMap = new ConcurrentHashMap<>();

    private final LinkedBlockingQueue<Runnable> regionRailwayLoadQueue = new LinkedBlockingQueue<Runnable>();
    private final ThreadPoolExecutor regionRailwayLoadPoolExecutor = new ThreadPoolExecutor(64, 1024, 1, TimeUnit.DAYS, regionRailwayLoadQueue);
    private final WorldGenRegion level;

    private RailwayBuilder(WorldGenRegion level) {
        this.level = level;
    }

    public static synchronized RailwayBuilder getInstance(long seed, WorldGenRegion level) {
        if (instance == null || RailwayBuilder.seed != seed) {
            instance = new RailwayBuilder(level);
            RailwayBuilder.seed = seed;
        }
        return instance;
    }

    public static synchronized RailwayBuilder getInstance(long seed) {
        if (instance == null || RailwayBuilder.seed != seed) {
            return null;
        }
        return instance;
    }

    // Generate railway routes for a region. Uses threads for generation.
    // Only plans routes, does not place them.
    public void generateRailway(RegionPos regionPos) {
        // If railway already generated, return
        if (regionRailways.containsKey(regionPos)) {
            return;
        }

        // Try to read from local save data
        ModSaveData data = ModSaveData.get(Objects.requireNonNull(level.getServer()).getLevel(ServerLevel.OVERWORLD));
        RailwayMap savedData = data.getRailwayMap(regionPos);
        if (savedData != null) {
            regionRailways.put(regionPos, savedData);
            Tongdarailway.LOGGER.info("Region {} Done! Read From Local Data", regionPos);
            return;
        }

        // If railway not yet generated...
        try {
            // If no thread generating this route, start one
            if (!regionFutures.containsKey(regionPos)) {
                var f = regionRailwayLoadPoolExecutor.submit(() -> {
                    // Generate railway map...
                    RailwayMap railwayMap = new RailwayMap(regionPos);

                    railwayMap.startPlanningRoutes(level);

                    // Save the railway planning result
                    regionRailways.put(regionPos, railwayMap);

                    // Save data to local
                    data.putRailwayMap(regionPos, railwayMap);
                });
                regionFutures.put(regionPos, f);
            }
            // Wait for thread to complete
            regionFutures.get(regionPos).get();
        } catch (InterruptedException | ExecutionException e) {
            Tongdarailway.LOGGER.error(e.getMessage());
        } finally {
            regionFutures.remove(regionPos);
        }
    }
}
