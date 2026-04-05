package com.xkmxz.tongdarailway_for_forge.railway;

import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.xkmxz.tongdarailway_for_forge.util.ModSaveData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;

import java.util.*;
import java.util.concurrent.*;

/**
 * 铁路生成管理器（单例）
 * 负责每个区域（RegionPos）的铁路规划、缓存与持久化。
 * 采用 LRU 缓存策略，避免内存无限增长。
 */
public class RailwayBuilder {
    private static RailwayBuilder instance;
    private static long seed;

    // 正在生成中的任务（避免重复生成）
    private final Map<RegionPos, Future<?>> regionFutures = new ConcurrentHashMap<>();
    // 内存缓存：区域 -> 铁路数据
    public final Map<RegionPos, RailwayMap> regionRailways = new ConcurrentHashMap<>();
    // 内存缓存：区域 -> 高度图（用于寻路）
    public final Map<RegionPos, int[][]> regionHeightMap = new ConcurrentHashMap<>();

    // ---------- LRU 缓存控制 ----------
    private final Map<RegionPos, Long> lastAccessTime = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;   // 最大缓存区域数（可根据内存调整）
    private static final int REMOVE_COUNT = MAX_CACHE_SIZE / 2; // 每次清理移除一半

    private final LinkedBlockingQueue<Runnable> regionRailwayLoadQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor regionRailwayLoadPoolExecutor =
            new ThreadPoolExecutor(64, 1024, 1, TimeUnit.DAYS, regionRailwayLoadQueue);
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

    /**
     * 为指定区域生成铁路规划（线程安全，带缓存）
     * 如果已存在则直接返回，否则触发异步生成并等待完成。
     */
    public void generateRailway(RegionPos regionPos) {
        // 1. 更新访问时间（LRU 记录）
        lastAccessTime.put(regionPos, System.currentTimeMillis());

        // 2. 如果已缓存，直接返回
        if (regionRailways.containsKey(regionPos)) {
            cleanupIfNeeded(); // 每次命中时也尝试清理，保持缓存健康
            return;
        }

        // 3. 尝试从持久化数据加载
        ModSaveData data = ModSaveData.get(Objects.requireNonNull(level.getServer()).getLevel(ServerLevel.OVERWORLD));
        RailwayMap savedData = data.getRailwayMap(regionPos);
        if (savedData != null) {
            regionRailways.put(regionPos, savedData);
            // 高度图可能也需要加载（此处简单起见，让实际使用时惰性生成）
            Tongdarailway_for_forge.LOGGER.info("Region {} loaded from disk", regionPos);
            cleanupIfNeeded();
            return;
        }

        // 4. 未命中，异步生成并阻塞等待
        Future<?> future;
        try {
            future = regionFutures.computeIfAbsent(regionPos, k ->
                    regionRailwayLoadPoolExecutor.submit(() -> {
                        RailwayMap railwayMap = new RailwayMap(regionPos);
                        railwayMap.startPlanningRoutes(level);
                        regionRailways.put(regionPos, railwayMap);
                        data.putRailwayMap(regionPos, railwayMap);
                        Tongdarailway_for_forge.LOGGER.info("Region {} generated and saved", regionPos);
                    })
            );
            future.get(); // 阻塞直到生成完成
        } catch (InterruptedException | ExecutionException e) {
            Tongdarailway_for_forge.LOGGER.error("Failed to generate railway for region " + regionPos, e);
        } finally {
            regionFutures.remove(regionPos);
        }

        // 5. 生成完成后清理旧缓存
        cleanupIfNeeded();
    }

    /**
     * 获取指定区域的高度图（若不存在则生成并缓存）
     * 该方法被 RoutePlanner 调用，同样需要更新 LRU 访问时间。
     */
    public int[][] getHeightMap(RegionPos regionPos, java.util.function.Function<RegionPos, int[][]> loader) {
        // 更新访问时间（因为高度图与铁路数据强相关）
        lastAccessTime.put(regionPos, System.currentTimeMillis());
        return regionHeightMap.computeIfAbsent(regionPos, loader);
    }

    /**
     * LRU 缓存清理：当缓存超过最大容量时，移除最久未访问的一半条目。
     * 注意：清理时同时移除 regionRailways 和 regionHeightMap 中的对应数据。
     */
    private void cleanupIfNeeded() {
        if (regionRailways.size() <= MAX_CACHE_SIZE) {
            return;
        }

        // 收集所有区域及其最后访问时间，并按时间升序排序（最旧的在前）
        List<Map.Entry<RegionPos, Long>> entries = new ArrayList<>(lastAccessTime.entrySet());
        entries.sort(Map.Entry.comparingByValue());

        // 计算要移除的数量（最多保留 MAX_CACHE_SIZE 个，实际移除一半或超出部分）
        int toRemove = Math.min(entries.size() - MAX_CACHE_SIZE, REMOVE_COUNT);
        if (toRemove <= 0) return;

        Set<RegionPos> toRemoveSet = new HashSet<>();
        for (int i = 0; i < toRemove; i++) {
            toRemoveSet.add(entries.get(i).getKey());
        }

        // 执行移除
        for (RegionPos pos : toRemoveSet) {
            regionRailways.remove(pos);
            regionHeightMap.remove(pos);
            lastAccessTime.remove(pos);
        }

        Tongdarailway_for_forge.LOGGER.debug("RailwayBuilder LRU cleanup: removed {} old regions (cache size now {})",
                toRemove, regionRailways.size());
    }

    // 可选：提供手动清理接口（如服务器停止时）
    public void clearCache() {
        regionRailways.clear();
        regionHeightMap.clear();
        lastAccessTime.clear();
        Tongdarailway_for_forge.LOGGER.info("RailwayBuilder cache cleared");
    }
}