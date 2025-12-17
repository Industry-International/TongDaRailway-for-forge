package com.hxzhitang.tongdarailway_for_forge.railway;

import com.hxzhitang.tongdarailway_for_forge.railway.planner.RoutePlanner;
import com.hxzhitang.tongdarailway_for_forge.railway.planner.StationPlanner;
import com.hxzhitang.tongdarailway_for_forge.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway_for_forge.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge.CHUNK_GROUP_SIZE;

public class RailwayMap {
    public static final int samplingNum = 2; // Sampling number per chunk

    public final RegionPos regionPos;

    // Per-region data
    // Routes
    public final Map<ChunkPos, Set<CurveRoute.CompositeCurve>> routeMap = new ConcurrentHashMap<>();
    // Stations
    public final List<StationPlanner.StationGenInfo> stations = new ArrayList<>();
    // Tracks
    public final Map<ChunkPos, List<TrackPutInfo>> trackMap = new ConcurrentHashMap<>();

    public RailwayMap(RegionPos regionPos) {
        this.regionPos = regionPos;
    }

    // Plan railway routes method
    public void startPlanningRoutes(WorldGenRegion level) {
        // Generate height map
        RoutePlanner routePlanner = new RoutePlanner(regionPos);
        int[][] costMap = routePlanner.getCostMap(level);

        // Generate station positions and connection planning
        StationPlanner stationPlanner = new StationPlanner(regionPos);
        stations.addAll(StationPlanner.generateStation(regionPos, level.getLevel(), level.getSeed()));
        var connections = stationPlanner.generateConnections(level.getLevel(), level.getSeed());

        // Generate route map
        for (StationPlanner.ConnectionGenInfo connection : connections) {
            // Convert to picture coordinate system
            int[] picStart = AStarPathfinder.world2PicPos(connection.connectStart(), regionPos);
            int[] picEnd = AStarPathfinder.world2PicPos(connection.connectEnd(), regionPos);
            List<int[]> way = AStarPathfinder.findPath(costMap, picStart, picEnd,
                    (x, y) -> {
                        int scopeLimit = scopeLimit(x, y, picStart, picEnd);
                        int heightLimit = costMap[x][y] < level.getSeaLevel() + 2 ? 100 : 0;
                        return scopeLimit + heightLimit;
                    });

            // Set actual route
            var route = routePlanner.getWay(way, costMap, connection, level.getLevel());
            putChunk(route);
        }
    }

    /**
     * Put route to chunks
     * @param route route
     */
    private void putChunk(RoutePlanner.ResultWay route) {
        for (CurveRoute.CurveSegment segment : route.way().getSegments()) {
            for (CurveRoute.Point3D p : segment.rasterize(16)) {
                for (int i = -1; i < 2; i++) {
                    for (int j = -1; j < 2; j++) {
                        int cx = (int) Math.floor(p.x) + i;
                        int cz = (int) Math.floor(p.z) + j;
                        if (cx >= regionPos.x() * CHUNK_GROUP_SIZE && cx < (regionPos.x() + 1) * CHUNK_GROUP_SIZE && cz >= regionPos.z() * CHUNK_GROUP_SIZE && cz < (regionPos.z() + 1) * CHUNK_GROUP_SIZE) {
                            routeMap.computeIfAbsent(new ChunkPos(cx, cz), k -> new HashSet<>())
                                    .add(route.way());
                        }
                    }
                }
            }
        }

        for (TrackPutInfo track : route.trackPutInfos()) {
            var pos = track.pos();
            trackMap.computeIfAbsent(new ChunkPos(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16)), k -> new ArrayList<>()).add(track);
        }
    }

    public static int scopeLimit(int x, int z, int[] picStart, int[] picEnd) {
        // Pathfinding scope limit
        int maxCost = 10000;
        int A = 64;

        double length = new Vec2(picEnd[0] - picStart[0], picEnd[1] - picStart[1]).length();

        Vec3 p = new Vec3(x, 0, z);

        Vec3 va = new Vec3(picEnd[0] - picStart[0], 0, picEnd[1] - picStart[1]).normalize();
        Vec3 vert = new Vec3(0, 1, 0);
        Vec3 vb = va.cross(vert);

        double a = p.dot(va) / length;
        if (a < 0 || a > 1)
            return maxCost;

        double b = Math.abs(p.dot(vb));
        double py = A * Math.sin(Math.PI * a);

        if (b > py)
            return maxCost;

        return 0;
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("RegionPos", regionPos.toNBT());

        // Save stations
        ListTag stationTag = new ListTag();
        stations.forEach(station -> stationTag.add(station.toNBT()));
        nbt.put("Stations", stationTag);

        // Save routes
        List<CurveRoute.CompositeCurve> palette = new ArrayList<>();
        ListTag routeMapTag = new ListTag();
        routeMap.forEach((pos, routes) -> {
            CompoundTag chunkNbt = new CompoundTag();
            chunkNbt.putInt("ChunkPosX", pos.x);
            chunkNbt.putInt("ChunkPosZ", pos.z);
            ListTag routesTag = new ListTag();
            for (CurveRoute.CompositeCurve route : routes) {
                int index;
                if (palette.contains(route)) {
                    index = palette.indexOf(route);
                } else {
                    palette.add(route);
                    index = palette.size() - 1;
                }
                routesTag.add(IntTag.valueOf(index));
            }
            chunkNbt.put("Routes", routesTag);
            routeMapTag.add(chunkNbt);
        });
        ListTag paletteTag = new ListTag();
        for (CurveRoute.CompositeCurve route : palette) {
            paletteTag.add(route.toNBT());
        }
        nbt.put("RouteMap", routeMapTag);
        nbt.put("RoutePalette", paletteTag);

        // Save tracks
        ListTag trackMapTag = new ListTag();
        trackMap.forEach((pos, tracks) -> {
            CompoundTag chunkNbt = new CompoundTag();
            chunkNbt.putInt("ChunkPosX", pos.x);
            chunkNbt.putInt("ChunkPosZ", pos.z);
            ListTag tracksTag = new ListTag();
            for (TrackPutInfo track : tracks) {
                tracksTag.add(track.toNBT());
            }
            chunkNbt.put("Tracks", tracksTag);
            trackMapTag.add(chunkNbt);
        });
        nbt.put("TrackMap", trackMapTag);

        return nbt;
    }

    public static RailwayMap fromNBT(CompoundTag nbt) {
        RegionPos regionPos = RegionPos.fromNBT((ListTag) nbt.get("RegionPos"));
        RailwayMap railwayMap = new RailwayMap(regionPos);

        // Read stations
        ListTag stationTag = (ListTag) nbt.get("Stations");
        if (stationTag != null) {
            for (net.minecraft.nbt.Tag tag : stationTag) {
                StationPlanner.StationGenInfo station = StationPlanner.StationGenInfo.fromNBT((CompoundTag) tag);
                railwayMap.stations.add(station);
            }
        }

        // Read routes
        ListTag routeTag = (ListTag) nbt.get("RouteMap");
        ListTag paletteTag = (ListTag) nbt.get("RoutePalette");
        List<CurveRoute.CompositeCurve> palette = new ArrayList<>();
        if (paletteTag != null && routeTag != null) {
            for (Tag tag : paletteTag) {
                palette.add(CurveRoute.CompositeCurve.fromNBT((ListTag) tag));
            }
            for (net.minecraft.nbt.Tag tag : routeTag) {
                CompoundTag chunkNbt = (CompoundTag) tag;
                ChunkPos chunkPos = new ChunkPos(chunkNbt.getInt("ChunkPosX"), chunkNbt.getInt("ChunkPosZ"));
                if (chunkNbt.contains("Routes")) {
                    Set<CurveRoute.CompositeCurve> routes = new HashSet<>();
                    for (net.minecraft.nbt.Tag tag1 : chunkNbt.getList("Routes", Tag.TAG_INT)) {
                        int index = ((IntTag) tag1).getAsInt();
                        CurveRoute.CompositeCurve route = palette.get(index);
                        routes.add(route);
                    }
                    railwayMap.routeMap.put(chunkPos, routes);
                }
            }
        }

        // Read tracks
        ListTag trackTag = (ListTag) nbt.get("TrackMap");
        if (trackTag != null) {
            for (net.minecraft.nbt.Tag tag : trackTag) {
                CompoundTag chunkNbt = (CompoundTag) tag;
                ChunkPos chunkPos = new ChunkPos(chunkNbt.getInt("ChunkPosX"), chunkNbt.getInt("ChunkPosZ"));
                if (chunkNbt.contains("Tracks")) {
                    List<TrackPutInfo> tracks = new ArrayList<>();
                    for (net.minecraft.nbt.Tag tag1 : chunkNbt.getList("Tracks", Tag.TAG_COMPOUND)) {
                        TrackPutInfo track = TrackPutInfo.fromNBT((CompoundTag) tag1);
                        tracks.add(track);
                    }
                    railwayMap.trackMap.put(chunkPos, tracks);
                }
            }
        }

        return railwayMap;
    }
}
