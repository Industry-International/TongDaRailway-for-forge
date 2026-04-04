package com.xkmxz.tongdarailway_for_forge.railway.planner;

import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.xkmxz.tongdarailway_for_forge.railway.RailwayBuilder;
import com.xkmxz.tongdarailway_for_forge.railway.RegionPos;
import com.xkmxz.tongdarailway_for_forge.structure.TrackPutInfo;
import com.xkmxz.tongdarailway_for_forge.util.*;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

import com.xkmxz.tongdarailway_for_forge.Config;
import static com.xkmxz.tongdarailway_for_forge.railway.RailwayMap.samplingNum;



/**
 * 路径查找和铁路路线规划类
 * 负责规划铁路线路，处理高度调整和水域检测
 */
public class RoutePlanner {
    private final RegionPos regionPos;

    public RoutePlanner(RegionPos regionPos) {
        this.regionPos = regionPos;
    }

    /**
     * 从九个相邻区域获取代价地图
     * ==================== 旧版方法 ====================
     * @param level 世界生成区域
     * @return 高度地图
     */
    public int[][] getCostMap(WorldGenRegion level) {
        int[][] heightMap = new int[Config.chunkGroupSize * samplingNum * 3][Config.chunkGroupSize * samplingNum * 3];
        for (int[] ints : heightMap) {
            Arrays.fill(ints, Integer.MAX_VALUE);
        }
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (Math.abs(i) == 1 && Math.abs(j) == 1)
                    continue;
                RegionPos rPos = new RegionPos(regionPos.x() + i, regionPos.z() + j);
                RailwayBuilder builder = RailwayBuilder.getInstance(level.getSeed());
                int[][] map;
                if (builder != null) {
                    map = builder.regionHeightMap
                            .computeIfAbsent(rPos, k -> getHeightMap(level.getLevel(), rPos));
                } else {
                    map = getHeightMap(level.getLevel(), rPos);
                }
                for (int x = 0; x < map.length; x++) {
                    for (int z = 0; z < map[0].length; z++) {
                        int picX = (i + 1) * Config.chunkGroupSize * samplingNum + x;
                        int picZ = (j + 1) * Config.chunkGroupSize * samplingNum + z;
                        heightMap[picX][picZ] = map[x][z];
                    }
                }
            }
        }
        return heightMap;
    }

    public CellCost[][] getCostMapWithObstacles(WorldGenRegion level, ServerLevel serverLevel) {
        int size = Config.chunkGroupSize * samplingNum * 3;
        CellCost[][] costMap = new CellCost[size][size];

        return costMap;
    }

    private CellCost computeCellCost(ServerLevel level, int worldX, int worldZ, int surfaceHeight) {

        int railY = surfaceHeight + 1;
        boolean blocked = false;
        double cost = 0.0;

        BlockPos pos = new BlockPos(worldX, railY, worldZ);
        BlockState state = level.getBlockState(pos);

        if (!state.isAir() && state.isSolid()) {
            blocked = true;
            cost = Double.POSITIVE_INFINITY;
        } else if (state.getBlock() == Blocks.WATER) {
            cost += 15.0;
        } else if (state.getBlock() instanceof LeavesBlock) {
            cost += 2.0;
        }

        return new CellCost(surfaceHeight, cost, blocked);
    }

    /**
     * 构建障碍物代价地图
     * 检测水域、固体方块等障碍物，为路径规划提供代价信息
     * @param level 服务器世界
     * @param heightMap 高度地图
     * @return 代价地图
     */
    private double[][] buildObstacleCostMap(ServerLevel level, int[][] heightMap) {
        int size = heightMap.length;
        double[][] costMap = new double[size][size];

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int worldX = (int) ((x - Config.chunkGroupSize * samplingNum) * (16.0 / samplingNum)
                        + regionPos.x() * Config.chunkGroupSize * 16);
                int worldZ = (int) ((z - Config.chunkGroupSize * samplingNum) * (16.0 / samplingNum)
                        + regionPos.z() * Config.chunkGroupSize * 16);
                int surfaceY = heightMap[x][z];

                int railY = surfaceY + 1;
                BlockPos pos = new BlockPos(worldX, railY, worldZ);
                BlockState state = level.getBlockState(pos);

                double cost = 0.0;
                if (!state.isAir() && state.isSolid()) {
                    // 固体方块，无法通过
                    cost = Double.POSITIVE_INFINITY;
                } else if (state.getBlock() == Blocks.WATER || state.is(BlockTags.GEODE_INVALID_BLOCKS)) {
                    // 水域或无效方块，增加代价
                    cost = 15.0;
                } else if (state.getBlock() instanceof LeavesBlock) {
                    // 树叶方块，轻微代价
                    cost = 2.0;
                } else if (state.is(BlockTags.BEDS)) {
                    // 床方块，高代价
                    cost = 100.0;
                }
                
                // 检测周围是否有水域，扩大水域检测范围
                // 如果在周围3格范围内检测到水，增加额外代价
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        BlockPos nearbyPos = new BlockPos(worldX + dx, railY, worldZ + dz);
                        BlockState nearbyState = level.getBlockState(nearbyPos);
                        if (nearbyState.getBlock() == Blocks.WATER) {
                            // 根据距离调整代价，越近代价越高
                            double distance = Math.sqrt(dx * dx + dz * dz);
                            cost += 5.0 / (distance + 1.0);
                        }
                    }
                }
                
                costMap[x][z] = cost;
            }
        }
        return costMap;
    }


    /**
     * 查找避开障碍物的路径
     * @param heightMap 高度地图
     * @param start 起点坐标
     * @param end 终点坐标
     * @param level 服务器世界
     * @return 路径点列表
     */
    public List<int[]> findPathAvoidObstacles(int[][] heightMap, int[] start, int[] end, ServerLevel level) {
        double[][] obstacleCost = buildObstacleCostMap(level, heightMap);

        return AStarPathfinder.findPath(heightMap, start, end, (x, z) -> obstacleCost[x][z]);
    }

    private int[][] getHeightMap(ServerLevel serverLevel, RegionPos regionPos) {
        // Height adaptive sampling height map
        ChunkGenerator gen = serverLevel.getChunkSource().getGenerator();
        RandomState cfg = serverLevel.getChunkSource().randomState();

        // Create adaptive height sampler
        AdaptiveHeightSampler sampler = new AdaptiveHeightSampler(10, 3, 4, (x, z) -> {
            int wx = (int) (x * (16.0 / samplingNum) + regionPos.x() * Config.chunkGroupSize * 16);
            int wz = (int) (z * (16.0 / samplingNum) + regionPos.z() * Config.chunkGroupSize * 16);
            return gen.getBaseHeight(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, serverLevel, cfg);
        });

        try {
            long startTime = System.currentTimeMillis();
            sampler.buildQuadTree(Config.chunkGroupSize * samplingNum);
            long endTime = System.currentTimeMillis();
            Tongdarailway_for_forge.LOGGER.info(" Build HeightMap time: {}ms", endTime - startTime);
        } catch (InterruptedException e) {
            Tongdarailway_for_forge.LOGGER.error(e.getMessage());
        } finally {
            sampler.shutdown();
        }

        int[][] heightMap = sampler.generateImage(Config.chunkGroupSize * samplingNum, Config.chunkGroupSize * samplingNum);

        return heightMap;
    }

    /**
     * Plan route
     * @param way route map
     */
    public ResultWay getWay(List<int[]> way, int[][] costMap, StationPlanner.ConnectionGenInfo connectionGenInfo, ServerLevel level) {
        List<int[]> handledHeightWay = handleHeight(way, level, costMap, connectionGenInfo);
        // Convert to region coordinate system
        handledHeightWay = handledHeightWay.stream().map(AStarPathfinder::pic2RegionPos).toList();
        return connectTrackNew3(handledHeightWay, connectionGenInfo);
    }

    /**
     * Handle height smoothing
     * @param path direct path (picture coordinates)
     * @param level server level
     */
    public List<int[]> handleHeight(List<int[]> path, ServerLevel level, int[][] heightMap, StationPlanner.ConnectionGenInfo con) {
        List<double[]> adPath = new LinkedList<>();
        int seaLevel = level.getSeaLevel();

        // Initial pass
        for (int[] p : path) {
            int h = heightMap[p[0]][p[1]];
            // Limit height range
            h = Math.max(h, seaLevel + 5);
            h = Math.min(h, seaLevel + Config.heightMaxIncrement);
            adPath.add(new double[]{p[0], p[1], h});
        }

        adPath.get(0)[2] = con.connectStart()[2];
        adPath.get(adPath.size() - 1)[2] = con.connectEnd()[2];

        // Height adjustment
        adPath = adjustmentHeight(adPath);

        // Smoothing (excluding endpoints)
        int max = adPath.stream().mapToInt(p -> (int) p[2]).max().orElse(0);
        int min = adPath.stream().mapToInt(p -> (int) p[2]).min().orElse(0);
        int framed2 = ((max - min) / 2) + 1;

        if (adPath.size() > framed2 * 2 && framed2 * 2 >= 3) {
            // Smooth middle
            List<double[]> adPath1 = new ArrayList<>();
            adPath1.add(adPath.get(0));
            for (int i = 1; i < adPath.size() - 1; i++) {
                double mean = 0;
                int sum = 0;
                for (int j = i - framed2; j <= i + framed2; j++) {
                    if (j >= 0 && j < adPath.size()) {
                        mean += adPath.get(j)[2];
                        sum++;
                    } else if (j < 0) {
                        mean += adPath.get(0)[2];
                        sum++;
                    } else {
                        mean += adPath.get(adPath.size() - 1)[2];
                        sum++;
                    }
                }
                mean /= sum;
                adPath1.add(new double[]{adPath.get(i)[0], adPath.get(i)[1], mean});
            }
            adPath1.add(adPath.get(adPath.size() - 1));
            adPath = adPath1;

            // Smooth endpoints
            double fh = con.connectStart()[2];
            double lh = con.connectEnd()[2];
            if (adPath.size() > framed2 * 2 + 20) {
                for (int i = 1; i < framed2 + 10; i++) {
                    double t = (double) i / (framed2 + 10);
                    double sh = adPath.get(i)[2];
                    double eh = adPath.get(adPath.size() - 1 - i)[2];

                    adPath.get(i)[2] = fh * (1 - t) + sh * t;
                    adPath.get(adPath.size() - 1 - i)[2] = lh * (1 - t) + eh * t;
                }
            }
        }

        return adPath.stream()
                .map(arr -> Arrays.stream(arr)
                        .mapToInt(d -> (int) Math.round(d))
                        .toArray()
                )
                .collect(Collectors.toList());
    }

    private static List<double[]> adjustmentHeight(List<double[]> path) {
        List<double[]> adjustedPath = new ArrayList<>();
        if (path.size() < 2)
            return new LinkedList<>();
        double hStart = path.get(0)[2];
        double hEnd = path.get(path.size() - 1)[2];
        double pNum = path.size() - 1;

        // Calculate relative height
        List<double[]> heightList0 = new ArrayList<>();
        Map<Integer, List<double[]>> heightGroups = new HashMap<>();
        double distance = 0;
        for (int i = 0; i < path.size(); i++) {
            double[] point = path.get(i);
            double h = point[2] - hStart * ((pNum - i) / pNum) - hEnd * (i / pNum);
            if (i > 0) {
                double h0 = point[2];
                double h1 = path.get(i - 1)[2];
                distance += 1 + Math.abs(h0 - h1);
            }
            double[] p = {point[0], point[1], h, i, distance};
            heightList0.add(p);
            int hi = (int) h;
            heightGroups.computeIfAbsent(hi, k -> new ArrayList<>()).add(p);
        }
        double sec = Math.sqrt(Math.pow(heightList0.size(), 2) + Math.pow(Math.abs(hStart - hEnd), 2)) / (heightList0.size());

        // Main loop
        for (int j = 0; j < heightList0.size(); j++) {
            double[] thisPoint = heightList0.get(j);
            adjustedPath.add(new double[]{thisPoint[0], thisPoint[1], thisPoint[2]});
            int hd = 0;
            if (j < heightList0.size() - 1) {
                hd = (int) heightList0.get(j + 1)[2] - (int) thisPoint[2];
            }
            if (hd == 0)
                continue;
            double h = thisPoint[2];
            var group = heightGroups.get((int) h);
            int groupIndex = group.indexOf(thisPoint);
            if (groupIndex < group.size() - 1) {
                double[] nextSameHeightPoint = group.get(groupIndex + 1);
                int nextPointIndex = heightList0.indexOf(nextSameHeightPoint);
                double dA = thisPoint[4], dB = nextSameHeightPoint[4];
                double iA = thisPoint[3], iB = nextSameHeightPoint[3];
                boolean conditionBridge = hd < 0 && (iB - iA) * 4 * sec < dB - dA;
                boolean conditionTunnel = hd > 0 && (iB - iA) * 3 * sec < dB - dA;
                if (conditionBridge || conditionTunnel) {
                    for (int k = j; k < nextPointIndex; k++) {
                        double[] np1 = heightList0.get(k + 1);
                        adjustedPath.add(new double[]{np1[0], np1[1], thisPoint[2]});
                    }
                    j = nextPointIndex;
                }
            }
        }
        // Add back base height
        for (int i = 0; i < adjustedPath.size(); i++) {
            double[] p = adjustedPath.get(i);
            p[2] += hStart * ((pNum - i) / pNum) + hEnd * (i / pNum);
        }

        return adjustedPath;
    }

    // ==================== 从 1.21.1 迁移的核心算法 ====================
    private record ConnectInfo(Vec3 startPos, Vec3 startAxis, Vec3 endPos, Vec3 endAxis, int startExtent, int endExtent) {}

    private static ConnectInfo getConnect(BlockPos pos1, BlockPos pos2, Vec3 axis1, Vec3 axis2, boolean maximiseTurn) {
        Vec3 normedAxis1 = axis1.normalize();
        Vec3 normedAxis2 = axis2.normalize();

        Vec3 end1 = MyMth.getCurveStart(pos1, axis1);
        Vec3 end2 = MyMth.getCurveStart(pos2, axis2);

        double[] intersect = VecHelper.intersect(end1, end2, normedAxis1, normedAxis2, Direction.Axis.Y);
        boolean parallel = intersect == null;
        boolean skipCurve = false;

        Vec3 cross2 = normedAxis2.cross(new Vec3(0, 1, 0));

        double a1 = Mth.atan2(normedAxis2.z, normedAxis2.x);
        double a2 = Mth.atan2(normedAxis1.z, normedAxis1.x);
        double angle = a1 - a2;
        double ascend = end2.subtract(end1).y;
        double absAscend = Math.abs(ascend);

        int end1Extent = 0;
        int end2Extent = 0;

        if (parallel) {
            double[] sTest = VecHelper.intersect(end1, end2, normedAxis1, cross2, Direction.Axis.Y);
            if (sTest != null) {
                double t = Math.abs(sTest[0]);
                double u = Math.abs(sTest[1]);

                skipCurve = Mth.equal(u, 0);

                if (!skipCurve && sTest[0] < 0)
                    return new ConnectInfo(
                            new Vec3(pos1.getX(), pos1.getY(), pos1.getZ()),
                            axis1,
                            new Vec3(pos2.getX(), pos2.getY(), pos2.getZ()),
                            axis2,
                            end1Extent,
                            end2Extent
                    );

                if (skipCurve) {
                    double dist = VecHelper.getCenterOf(pos1).distanceTo(VecHelper.getCenterOf(pos2));
                    end1Extent = (int) Math.round((dist + 1) / axis1.length());
                } else {
                    if (!Mth.equal(ascend, 0) || normedAxis1.y != 0)
                        return null;

                    double targetT = u <= 1 ? 3 : u * 2;
                    if (t < targetT) return null;
                    if (t > targetT) {
                        int correction = (int) ((t - targetT) / axis1.length());
                        end1Extent = maximiseTurn ? 0 : correction / 2 + (correction % 2);
                        end2Extent = maximiseTurn ? 0 : correction / 2;
                    }
                }
            }
        }

        if (skipCurve && !Mth.equal(ascend, 0)) {
            int hDistance = end1Extent;
            if (axis1.y == 0 || !Mth.equal(absAscend + 1, hDistance)) {
                if (axis1.y != 0 && axis1.y == -axis2.y)
                    return null;

                end1Extent = 0;
                double minHDistance = Math.max(absAscend < 4 ? absAscend * 4 : absAscend * 3, 6) / axis1.length();
                if (hDistance < minHDistance) return null;
                if (hDistance > minHDistance) {
                    int correction = (int) (hDistance - minHDistance);
                    end1Extent = maximiseTurn ? 0 : correction / 2 + (correction % 2);
                    end2Extent = maximiseTurn ? 0 : correction / 2;
                }
                skipCurve = false;
            }
        }

        if (!parallel) {
            float absAngle = Math.abs(AngleHelper.deg(angle));
            if (absAngle < 60 || absAngle > 300) return null;

            intersect = VecHelper.intersect(end1, end2, normedAxis1, normedAxis2, Direction.Axis.Y);
            double dist1 = Math.abs(intersect[0]);
            double dist2 = Math.abs(intersect[1]);
            float ex1 = 0, ex2 = 0;

            if (dist1 > dist2) ex1 = (float) ((dist1 - dist2) / axis1.length());
            if (dist2 > dist1) ex2 = (float) ((dist2 - dist1) / axis2.length());

            double turnSize = Math.min(dist1, dist2) - .1d;
            boolean ninety = (absAngle + .25f) % 90 < 1;

            if (intersect[0] < 0 || intersect[1] < 0) return null;

            double minTurnSize = ninety ? 7 : 3.25;
            double turnSizeToFitAscend = minTurnSize + (ninety ? Math.max(0, absAscend - 3) * 2 : Math.max(0, absAscend - 1.5) * 1.5);

            if (turnSize < minTurnSize) return null;
            if (turnSize < turnSizeToFitAscend) return null;

            if (!maximiseTurn) {
                ex1 += (float) ((turnSize - turnSizeToFitAscend) / axis1.length());
                ex2 += (float) ((turnSize - turnSizeToFitAscend) / axis2.length());
            }
            end1Extent = Mth.floor(ex1);
            end2Extent = Mth.floor(ex2);
        }

        Vec3 offset1 = axis1.scale(end1Extent);
        Vec3 offset2 = axis2.scale(end2Extent);
        BlockPos startPos = pos1.offset((int) offset1.x, (int) offset1.y, (int) offset1.z);
        BlockPos endPos = pos2.offset((int) offset2.x, (int) offset2.y, (int) offset2.z);

        return new ConnectInfo(
                new Vec3(startPos.getX(), startPos.getY(), startPos.getZ()),
                axis1,
                new Vec3(endPos.getX(), endPos.getY(), endPos.getZ()),
                axis2,
                end1Extent,
                end2Extent
        );
    }

    private ResultWay connectTrackNew3(List<int[]> path, StationPlanner.ConnectionGenInfo con) {
        List<Vec3> path0 = new ArrayList<>();
        for (int i = 0; i < path.size() - 2; i++) {
            int[] point = path.get(i);
            path0.add(MyMth.inRegionPos2WorldPos(regionPos,
                    new Vec3(point[0], point[2], point[1]).multiply(16.0 / samplingNum, 1, 16.0 / samplingNum)));
        }

        List<Vec3> path1 = new ArrayList<>();
        for (int i = 0; i < path0.size() - 12; i += 6) path1.add(path0.get(i));
        path1.add(path0.get(path0.size() - 1));

        ResultWay result = new ResultWay(new CurveRoute.CompositeCurve(), new ArrayList<>());

        Vec3 pA = con.start().add(con.startDir().scale(30)).add(con.exitDir().scale(25));
        result.addBezier(con.start(), con.startDir(), pA.subtract(con.start()), con.exitDir().reverse());

        Vec3 pB = con.end().add(con.endDir().scale(30)).add(con.exitDir().reverse().scale(25));
        path1.add(0, pA);
        path1.add(pB);

        Vec3 startDir = con.exitDir();
        Vec3 endDir;
        for (int i = 0; i < path1.size() - 1; i++) {
            Vec3 start = path1.get(i);
            Vec3 end = path1.get(i + 1);
            endDir = MyMth.get8Dir(end.subtract(start)).reverse();
            if (i == path1.size() - 2) endDir = con.exitDir().reverse();

            if (RoutePlanner.getConnect(BlockPos.containing(start.multiply(1,0,1)), BlockPos.containing(end.multiply(1,0,1)), startDir, endDir, false) != null) {
                Vec3 dir = end.subtract(start).multiply(1, 0, 1).normalize();
                if (Mth.equal(startDir.dot(dir), 1) && Mth.equal(startDir.dot(endDir.reverse()), 1))
                    result.addBezier(start, startDir, end.subtract(start), endDir);
                else
                    result.connectWay(start, end, startDir, endDir, start.y == end.y);
            } else {
                int len = (int) (start.distanceTo(end) / 2) - 2;
                Vec3 aPos = start.add(startDir.scale(len)).add(end.add(endDir.scale(len))).scale(0.5);
                aPos = new Vec3((int) aPos.x(), (int) aPos.y(), (int) aPos.z());
                Vec3 aDir;
                Vec3 d = aPos.subtract(start).multiply(1, 0, 1).normalize();
                double dot = startDir.dot(d);
                double cross = startDir.x * d.z - startDir.z * d.x;
                boolean maximiseTurn = start.y == end.y;

                if (Mth.equal(dot, 1)) {
                    aDir = startDir;
                    result.addBezier(start, startDir, aPos.subtract(start), aDir.reverse());
                    result.addBezier(aPos, aDir, end.subtract(aPos), endDir);
                } else {
                    aDir = dot > 0.78 ? MyMth.rotateAroundY(startDir, cross, 45) : MyMth.rotateAroundY(startDir, cross, 90);
                    result.connectWay(start, aPos, startDir, aDir.reverse(), maximiseTurn);
                    result.connectWay(aPos, end, aDir, endDir, maximiseTurn);
                }
            }
            startDir = endDir.reverse();
        }
        result.addBezier(pB, con.exitDir(), con.end().subtract(pB), con.endDir());
        return result;
    }

    // ==================== ResultWay 记录 ====================
    public record ResultWay(CurveRoute.CompositeCurve way, List<TrackPutInfo> trackPutInfos) {

        public void connectWay(Vec3 start, Vec3 end, Vec3 startDir, Vec3 endDir, boolean maximiseTurn) {
            int h = (int) ((start.y + end.y) / 2);
            Vec3 s = new Vec3(start.x, h, start.z);
            Vec3 e = new Vec3(end.x, h, end.z);
            var connect = RoutePlanner.getConnect(BlockPos.containing(s), BlockPos.containing(e), startDir, endDir, maximiseTurn);
            if (connect != null) {
                if (connect.startExtent < 4 || connect.endExtent < 4) {
                    h = connect.startExtent < connect.endExtent ? (int) start.y : (int) end.y;
                }
                Vec3 conStart = new Vec3(connect.startPos.x, h, connect.startPos.z);
                Vec3 conEnd = new Vec3(connect.endPos.x, h, connect.endPos.z);
                if (connect.startExtent != 0) addBezier(start, startDir, conStart.subtract(start), startDir.reverse());
                addBezier(conStart, startDir, conEnd.subtract(conStart), endDir);
                if (connect.endExtent != 0) addBezier(conEnd, endDir.reverse(), end.subtract(conEnd), endDir);
            } else {
                addBezier(start, startDir, end.subtract(start), endDir);
                Tongdarailway_for_forge.LOGGER.warn("The road position cannot be determined, and the line has been forced to connect. {} {}", start, end);
            }
        }

        public void addLine(Vec3 start, Vec3 end) {
            way.addSegment(new CurveRoute.LineSegment(start, end));
            int n = Math.max((int) Math.abs(start.x - end.x), (int) Math.abs(start.z - end.z));
            for (int k = 0; k <= n; k++) {
                int x = (int) (start.x + MyMth.getSign(end.x - start.x) * k);
                int z = (int) (start.z + MyMth.getSign(end.z - start.z) * k);
                trackPutInfos.add(TrackPutInfo.getByDir(new BlockPos(x, (int) start.y, z), end.subtract(start), null));
            }
        }

        public void addBezier(Vec3 start, Vec3 startDir, Vec3 endOffset, Vec3 endDir) {
            if (Math.abs(startDir.dot(endDir)) > 0.9999 && startDir.dot(endOffset.normalize()) > 0.9999) {
                way.addSegment(new CurveRoute.LineSegment(start, start.add(endOffset)));
            } else {
                way.addSegment(CurveRoute.CubicBezier.getCubicBezier(start, startDir, endOffset, endDir));
            }
            trackPutInfos.add(TrackPutInfo.getByDir(new BlockPos((int) start.x, (int) start.y, (int) start.z),
                    startDir, new TrackPutInfo.BezierInfo(start, startDir, endOffset, endDir)));
        }
    }
}