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
    public final Map<RegionPos, RailwayMap> regionRailways = new ConcurrentHashMap<>();

    public void putRailwayMap(RegionPos regionPos, RailwayMap railwayMap) {
        regionRailways.put(regionPos, railwayMap);
        setDirty();

        // 内存泄漏修复：限制缓存大小，最多保留 100 个区域
        if (regionRailways.size() > 100) {
            // 移除最早的一半（这里简单移除 keySet 的前一半，实际可改进为 LRU）
            List<RegionPos> toRemove = new ArrayList<>();
            int removeCount = regionRailways.size() / 2;
            for (RegionPos pos : regionRailways.keySet()) {
                if (toRemove.size() >= removeCount) break;
                toRemove.add(pos);
            }
            for (RegionPos pos : toRemove) {
                regionRailways.remove(pos);
            }
            Tongdarailway_for_forge.LOGGER.info("ModSaveData: cleaned {} old region data", toRemove.size());
        }
    }

    public RailwayMap getRailwayMap(RegionPos regionPos) {
        setDirty();
        return regionRailways.get(regionPos);
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
