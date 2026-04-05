package com.xkmxz.tongdarailway_for_forge.util;

import com.xkmxz.tongdarailway_for_forge.railway.RailwayMap;
import com.xkmxz.tongdarailway_for_forge.railway.RegionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;

public class ModSaveData extends SavedData {
    public static final String NAME = "tongdarailway_mod_railway_data";
    // 实际数据
    private final Map<RegionPos, RailwayMap> regionRailways = new ConcurrentHashMap<>();
    // 记录每个区域最后访问时间（毫秒）
    private final Map<RegionPos, Long> lastAccessTime = new ConcurrentHashMap<>();

    // 缓存大小上限（根据内存情况调整，建议 100~200）
    private static final int MAX_CACHE_SIZE = 150;
    // 每次清理移除的数量（保留最近的一半）
    private static final int REMOVE_COUNT = MAX_CACHE_SIZE / 2;

    public void putRailwayMap(RegionPos regionPos, RailwayMap railwayMap) {
        regionRailways.put(regionPos, railwayMap);
        lastAccessTime.put(regionPos, System.currentTimeMillis());
        setDirty();
        // 写入后检查缓存大小，必要时清理
        cleanupIfNeeded();
    }

    public RailwayMap getRailwayMap(RegionPos regionPos) {
        RailwayMap map = regionRailways.get(regionPos);
        if (map != null) {
            // 更新访问时间
            lastAccessTime.put(regionPos, System.currentTimeMillis());
        }
        setDirty();
        return map;
    }

    /**
     * 当缓存超过最大容量时，移除最久未访问的一半条目。
     * 注意：该方法会遍历整个缓存，但调用频率低（仅在超出阈值时），性能可接受。
     */
    private void cleanupIfNeeded() {
        if (regionRailways.size() <= MAX_CACHE_SIZE) {
            return;
        }
        // 收集所有条目并按最后访问时间排序（升序，即最旧的在前）
        List<Map.Entry<RegionPos, Long>> entries = new ArrayList<>(lastAccessTime.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        // 计算要移除的数量（最多保留 MAX_CACHE_SIZE 个，实际移除一半）
        int toRemove = Math.min(entries.size() - MAX_CACHE_SIZE, REMOVE_COUNT);
        if (toRemove <= 0) return;

        // 移除最旧的 toRemove 个条目
        for (int i = 0; i < toRemove; i++) {
            RegionPos pos = entries.get(i).getKey();
            regionRailways.remove(pos);
            lastAccessTime.remove(pos);
        }
        Tongdarailway_for_forge.LOGGER.debug("ModSaveData: cleaned {} old region data (LRU)", toRemove);
    }

    public static ModSaveData create() {
        return new ModSaveData();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag pCompoundTag) {
        ListTag listTag = new ListTag();
        regionRailways.forEach((pos, railwayMap) -> {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("RegionPos", pos.toNBT());
            compoundTag.put("RailwayMap", railwayMap.toNBT());
            listTag.add(compoundTag);
        });
        pCompoundTag.put("RailwayData", listTag);
        return pCompoundTag;
    }

    public static ModSaveData load(CompoundTag nbt) {
        ModSaveData data = ModSaveData.create();
        ListTag listNBT = (ListTag) nbt.get("RailwayData");
        if (listNBT != null) {
            for (Tag value : listNBT) {
                CompoundTag tag = (CompoundTag) value;
                RegionPos regionPos = RegionPos.fromNBT((ListTag) tag.get("RegionPos"));
                CompoundTag dataTag = (CompoundTag) tag.get("RailwayMap");
                RailwayMap railwayMap;
                if (dataTag != null) {
                    railwayMap = RailwayMap.fromNBT(dataTag);
                    data.regionRailways.put(regionPos, railwayMap);
                }
            }
        }

        return data;
    }

    public static ModSaveData get(Level worldIn) {
        if (!(worldIn instanceof ServerLevel)) {
            throw new RuntimeException("Attempted to get the data from a client world. This is wrong.");
        }
        ServerLevel world = worldIn.getServer().getLevel(ServerLevel.OVERWORLD);
        DimensionDataStorage dataStorage = world.getDataStorage();
        return dataStorage.computeIfAbsent(ModSaveData::load, ModSaveData::create, ModSaveData.NAME);
    }
}
