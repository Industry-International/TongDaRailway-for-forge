package com.xkmxz.tongdarailway_for_forge.railway;

import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.xkmxz.tongdarailway_for_forge.util.ModSaveData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;

import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RailwayBuilder {
    private static RailwayBuilder instance;
    private static long seed;

    private final Map<RegionPos, Future<?>> regionFutures = new ConcurrentHashMap<>();
    public final Map<RegionPos, RailwayMap> regionRailways = new ConcurrentHashMap<>();
    public final Map<RegionPos, int[][]> regionHeightMap = new ConcurrentHashMap<>();

    private final LinkedBlockingQueue<Runnable> regionRailwayLoadQueue = new LinkedBlockingQueue<Runnable>();
    // 优化线程池配置：采用折中方案，平衡性能和资源消耗
    private final ThreadPoolExecutor regionRailwayLoadPoolExecutor = new ThreadPoolExecutor(
            16, // 核心线程数：适中值，保证足够的处理能力
            64, // 最大线程数：适中值，避免过度消耗资源
            30, // 线程保活时间：30秒，及时释放空闲线程
            TimeUnit.SECONDS, 
            regionRailwayLoadQueue
    );
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
            Tongdarailway_for_forge.LOGGER.info("Region {} Done! Read From Local Data", regionPos);
            return;
        }

        Future<?> future;
        try {
            future = regionFutures.computeIfAbsent(regionPos, k -> {
                return regionRailwayLoadPoolExecutor.submit(() -> {
                    // Generate railway map...
                    RailwayMap railwayMap = new RailwayMap(regionPos);
                    railwayMap.startPlanningRoutes(level);

                    // Save the railway planning result
                    regionRailways.put(regionPos, railwayMap);

                    // Save data to local
                    data.putRailwayMap(regionPos, railwayMap);
                });
            });
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            Tongdarailway_for_forge.LOGGER.error(e.getMessage());
        } finally {
            regionFutures.remove(regionPos);
        }

        // ========== 内存泄漏修复：限制缓存大小 ==========
        // 调整缓存阈值，平衡内存使用和性能
        int cacheThreshold = 100; // 减少缓存阈值，降低内存使用
        if (regionRailways.size() > cacheThreshold) {   // 阈值可调，100 区域约对应 100 * 128*128 方块
            List<RegionPos> toRemove = new ArrayList<>();
            int removeCount = regionRailways.size() / 2;   // 移除一半
            for (RegionPos pos : regionRailways.keySet()) {
                if (toRemove.size() >= removeCount) break;
                if (pos.equals(regionPos)) continue;   // 保留当前区域
                toRemove.add(pos);
            }
            for (RegionPos pos : toRemove) {
                regionRailways.remove(pos);
                regionHeightMap.remove(pos);
            }
            Tongdarailway_for_forge.LOGGER.info("Cleaned {} old railway data from memory", toRemove.size());
        }
    }
}
