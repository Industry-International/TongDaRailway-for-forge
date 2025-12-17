package com.hxzhitang.tongdarailway_for_forge.railway.planner;

import com.hxzhitang.tongdarailway_for_forge.railway.RegionPos;
import com.hxzhitang.tongdarailway_for_forge.structure.StationManager;
import com.hxzhitang.tongdarailway_for_forge.structure.StationStructure;
import com.hxzhitang.tongdarailway_for_forge.util.MyMth;
import com.hxzhitang.tongdarailway_for_forge.util.MyRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge.CHUNK_GROUP_SIZE;
import static com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge.HEIGHT_MAX_INCREMENT;

// Station planning and connection planning
public class StationPlanner {
    private final RegionPos regionPos;

    public StationPlanner(RegionPos regionPos) {
        this.regionPos = regionPos;
    }

    private static final int MAX_OCEAN_RETRY = 10; // Max attempts to find non-ocean position

    // Generate station position
    public static List<StationGenInfo> generateStation(RegionPos regionPos, ServerLevel level, long seed) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState cfg = level.getChunkSource().randomState();

        long regionSeed = seed + regionPos.hashCode();
        List<StationGenInfo> result = new ArrayList<>();

        int x, z, y;
        Holder<Biome> biome;

        // Try to find non-ocean position, up to MAX_OCEAN_RETRY attempts
        for (int attempt = 0; attempt <= MAX_OCEAN_RETRY; attempt++) {
            int[] pos = MyRandom.generatePoints(regionSeed + attempt * 1000, CHUNK_GROUP_SIZE);
            ChunkPos chunkPos = new ChunkPos(MyMth.chunkPosXFromRegionPos(regionPos, pos[0]), MyMth.chunkPosZFromRegionPos(regionPos, pos[1]));
            x = chunkPos.getBlockX(0);
            z = chunkPos.getBlockZ(0);
            y = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, cfg);

            biome = level.getBiome(new BlockPos(x, y, z));
            if (!biome.is(BiomeTags.IS_OCEAN)) {
                // Found non-ocean position, generate station
                int h = Math.max(y, level.getSeaLevel());
                h = Math.min(h, level.getSeaLevel() + HEIGHT_MAX_INCREMENT);

                StationStructure station;
                int placeH;
                if (h < y - 10) {
                    station = StationManager.getRandomUnderGroundStation(regionSeed);
                    placeH = h;
                } else {
                    station = StationManager.getRandomNormalStation(regionSeed);
                    placeH = h - 10;
                }
                result.add(new StationGenInfo(station, new BlockPos(x, placeH, z)));
                return result;
            }
        }

        // All attempts failed (entire region is ocean), return empty list
        return result;
    }

    // Route connection generation
    public List<ConnectionGenInfo> generateConnections(ServerLevel level, long seed) {
        List<ConnectionGenInfo> result = new ArrayList<>();

        List<StationGenInfo> thisStations = generateStation(regionPos, level, seed);

        // Skip if current region has no station (ocean biome)
        if (thisStations.isEmpty()) {
            return result;
        }

        List<StationGenInfo> north = generateStation(new RegionPos(regionPos.x(), regionPos.z() - 1), level, seed);
        List<StationGenInfo> south = generateStation(new RegionPos(regionPos.x(), regionPos.z() + 1), level, seed);

        List<StationGenInfo> east = generateStation(new RegionPos(regionPos.x() + 1, regionPos.z()), level, seed);
        List<StationGenInfo> west = generateStation(new RegionPos(regionPos.x() - 1, regionPos.z()), level, seed);

        var thisAssignedExits = assignExits(getExitsPos(thisStations));

        // Only connect to neighbors that have stations (not ocean)
        if (!east.isEmpty()) {
            var eastAssignedExits = assignExits(getExitsPos(east));
            result.add(ConnectionGenInfo.getConnectionInfo(thisAssignedExits.get(3), eastAssignedExits.get(2), new Vec3(1, 0, 0)));
        }
        if (!west.isEmpty()) {
            var westAssignedExits = assignExits(getExitsPos(west));
            result.add(ConnectionGenInfo.getConnectionInfo(westAssignedExits.get(3), thisAssignedExits.get(2), new Vec3(1, 0, 0)));
        }
        if (!north.isEmpty()) {
            var northAssignedExits = assignExits(getExitsPos(north));
            result.add(ConnectionGenInfo.getConnectionInfo(northAssignedExits.get(1), thisAssignedExits.get(0), new Vec3(0, 0, 1)));
        }
        if (!south.isEmpty()) {
            var southAssignedExits = assignExits(getExitsPos(south));
            result.add(ConnectionGenInfo.getConnectionInfo(thisAssignedExits.get(1), southAssignedExits.get(0), new Vec3(0, 0, 1)));
        }

        return result;
    }

    // Get all exits
    private List<StationStructure.Exit> getExitsPos(List<StationGenInfo> stations) {
        List<StationStructure.Exit> exits = new ArrayList<>();
        for (StationGenInfo station : stations) {
            BlockPos placePos = station.placePos;
            for (StationStructure.Exit exit : station.stationStructure.getExits()) {
                BlockPos offset = exit.exitPos();
                exits.add(new StationStructure.Exit(placePos.offset(offset), exit.dir()));
            }
        }

        return exits;
    }

    /**
     * Assign exits to directions
     * @param exits exit list
     * @return exit list in order: [N, S, W, E, ...]
     */
    private List<StationStructure.Exit> assignExits(List<StationStructure.Exit> exits) {
        if (exits.size() < 4) {
            return exits;
        }

        List<StationStructure.Exit> copy = new ArrayList<>(exits);

        List<StationStructure.Exit> result = new ArrayList<>();
        // Sort by z coordinate (with offset to avoid same z)
        copy.sort(Comparator.comparingDouble(e -> {
            int z = e.exitPos().getZ();
            Random random = new Random(75_1049 + z);
            double off = random.nextDouble() * 2 - 1;
            return z + off;
        }));

        result.add(copy.remove(0)); // smallest z - north
        result.add(copy.remove(copy.size() - 1));  // largest z - south

        // Sort by x coordinate
        copy.sort(Comparator.comparingDouble(e -> {
            int x = e.exitPos().getX();
            Random random = new Random(75_1052 + x);
            double off = random.nextDouble() * 2 - 1;
            return x + off;
        }));

        result.add(copy.remove(0));  // smallest x - west
        result.add(copy.remove(copy.size() - 1));   // largest x - east

        // Add remaining exits
        Set<StationStructure.Exit> addedExits = new HashSet<>(result);
        for (StationStructure.Exit exit : exits) {
            if (!addedExits.contains(exit)) {
                result.add(exit);
            }
        }

        return result; // N S W E
    }

    private static int[] getConnectStart(BlockPos exitPos, Vec3 dir, Vec3 offset, Vec3 exitDir) {
        Vec3 pos = Vec3.atCenterOf(exitPos);
        Vec3 addOff = exitDir.scale(40);
        Vec3 start = pos.add(dir.scale(30).add(addOff));
        return new int[]{(int) start.x, (int) start.z, exitPos.getY()};
    }

    // Station generation info (world coordinate system)
    public record StationGenInfo(
            StationStructure stationStructure,
            BlockPos placePos
    ) {
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", stationStructure.getId());
            tag.putString("type", stationStructure.getType().name());
            tag.putInt("x", placePos.getX());
            tag.putInt("y", placePos.getY());
            tag.putInt("z", placePos.getZ());
            return tag;
        }

        public static StationGenInfo fromNBT(CompoundTag tag) {
            int id = tag.getInt("id");
            int x = tag.getInt("x");
            int y = tag.getInt("y");
            int z = tag.getInt("z");
            StationStructure.StationType type = StationStructure.StationType.valueOf(tag.getString("type"));
            StationStructure stationStructure = null;
            switch (type) {
                case NORMAL -> {
                    if (StationManager.normalStation.containsKey(id)) {
                        stationStructure = StationManager.normalStation.get(id);
                    }
                }
                case UNDER_GROUND -> {
                    if (StationManager.undergroundStation.containsKey(id)) {
                        stationStructure = StationManager.undergroundStation.get(id);
                    }
                }
            }

            return new StationGenInfo(stationStructure, new BlockPos(x, y, z));
        }
    }

    /**
     * Route connection info (world coordinate system)
     */
    public record ConnectionGenInfo(
            Vec3 start,
            Vec3 startDir,
            Vec3 end,
            Vec3 endDir,
            int[] connectStart,
            int[] connectEnd,
            Vec3 exitDir
    ) {
        public static ConnectionGenInfo getConnectionInfo(StationStructure.Exit A, StationStructure.Exit B, Vec3 exitDir) {
            Vec3 APos = new Vec3(A.exitPos().getX(), A.exitPos().getY(), A.exitPos().getZ());
            Vec3 BPos = new Vec3(B.exitPos().getX(), B.exitPos().getY(), B.exitPos().getZ());
            return new ConnectionGenInfo(
                    APos,
                    A.dir(),
                    BPos,
                    B.dir(),
                    getConnectStart(A.exitPos(), A.dir(), BPos.subtract(APos), exitDir),
                    getConnectStart(B.exitPos(), B.dir(), APos.subtract(BPos), exitDir.reverse()),
                    exitDir
            );
        }
    }
}
