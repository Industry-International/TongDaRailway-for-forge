package com.hxzhitang.tongdarailway.railway.planner;

import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.railway.RailwayBuilder;
import com.hxzhitang.tongdarailway.railway.RegionPos;
import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.*;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

import static com.hxzhitang.tongdarailway.Tongdarailway.CHUNK_GROUP_SIZE;
import static com.hxzhitang.tongdarailway.Tongdarailway.HEIGHT_MAX_INCREMENT;
import static com.hxzhitang.tongdarailway.railway.RailwayMap.samplingNum;

// Pathfinding and railway route planning
public class RoutePlanner {
    private final RegionPos regionPos;

    public RoutePlanner(RegionPos regionPos) {
        this.regionPos = regionPos;
    }

    // Get cost map from nine adjacent regions
    public int[][] getCostMap(WorldGenRegion level) {
        int[][] heightMap = new int[CHUNK_GROUP_SIZE * samplingNum * 3][CHUNK_GROUP_SIZE * samplingNum * 3];
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
                        int picX = (i + 1) * CHUNK_GROUP_SIZE * samplingNum + x;
                        int picZ = (j + 1) * CHUNK_GROUP_SIZE * samplingNum + z;
                        heightMap[picX][picZ] = map[x][z];
                    }
                }
            }
        }

        return heightMap;
    }

    private int[][] getHeightMap(ServerLevel serverLevel, RegionPos regionPos) {
        // Height adaptive sampling height map
        ChunkGenerator gen = serverLevel.getChunkSource().getGenerator();
        RandomState cfg = serverLevel.getChunkSource().randomState();

        // Create adaptive height sampler
        AdaptiveHeightSampler sampler = new AdaptiveHeightSampler(10, 3, 4, (x, z) -> {
            int wx = (int) (x * (16.0 / samplingNum) + regionPos.x() * CHUNK_GROUP_SIZE * 16);
            int wz = (int) (z * (16.0 / samplingNum) + regionPos.z() * CHUNK_GROUP_SIZE * 16);
            return gen.getBaseHeight(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, serverLevel, cfg);
        });

        try {
            long startTime = System.currentTimeMillis();
            sampler.buildQuadTree(CHUNK_GROUP_SIZE * samplingNum);
            long endTime = System.currentTimeMillis();
            Tongdarailway.LOGGER.info(" Build HeightMap time: {}ms", endTime - startTime);
        } catch (InterruptedException e) {
            Tongdarailway.LOGGER.error(e.getMessage());
        } finally {
            sampler.shutdown();
        }

        int[][] heightMap = sampler.generateImage(CHUNK_GROUP_SIZE * samplingNum, CHUNK_GROUP_SIZE * samplingNum);

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
        return connectTrackNew(handledHeightWay, connectionGenInfo);
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
            h = Math.min(h, seaLevel + HEIGHT_MAX_INCREMENT);
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

    /**
     * Connect tracks using bezier curves
     * @param path path endpoints
     * @return connected track result
     */
    private ResultWay connectTrackNew(List<int[]> path, StationPlanner.ConnectionGenInfo con) {
        // Convert to world coordinate system
        List<Vec3> path0 = new ArrayList<>();

        for (int i = 2; i < path.size() - 2; i++) {
            int[] point = path.get(i);
            path0.add(MyMth.inRegionPos2WorldPos(
                    regionPos,
                    new Vec3(point[0], point[2], point[1])
                            .multiply(16.0 / samplingNum, 1, 16.0 / samplingNum)
            ));
        }

        // Connect to stations
        Vec3 first = path0.get(0);
        Vec3 last = path0.get(path0.size() - 1);
        Vec3 firstDir = first.subtract(path0.get(1)).normalize();
        Vec3 lastDir = last.subtract(path0.get(path0.size() - 2)).normalize();

        ResultWay result = new ResultWay(new CurveRoute.CompositeCurve(), new ArrayList<>());

        // Station start connection
        Vec3 pA = con.start().add(con.startDir().scale(30)).add(con.exitDir().scale(30));
        if (con.startDir().dot(con.exitDir()) > 0.999) {
            result.addLine(con.start(), pA);
        } else {
            result.addBezier(con.start(), con.startDir(), pA.subtract(con.start()), con.exitDir().reverse());
        }
        result.addBezier(pA, con.exitDir(), first.subtract(pA), firstDir);

        // From second point, find valid connections
        a:
        for (int i = 0; i < path0.size() - 1; i++) {
            int j = Math.min(8, path0.size() - 1 - i);
            Vec3 startPos = path0.get(i);
            Vec3 startDir;
            if (i == 0)
                startDir = path0.get(i + 1).subtract(path0.get(i)).multiply(1, 0, 1).normalize();
            else
                startDir = path0.get(i).subtract(path0.get(i - 1)).multiply(1, 0, 1).normalize();

            // Check forward points
            while (j >= 2) {
                Vec3 endPos0 = path0.get(i + j);
                Vec3 endDir0 = path0.get(i + j).subtract(path0.get(i + j - 1)).multiply(1, 0, 1).normalize();

                if (isValidTrackPlacement(startPos, startDir, endPos0, endDir0.reverse())) {
                    if (startPos.y == endPos0.y && startDir.dot(endDir0) > 0.9999 && startDir.dot(endPos0.subtract(startPos).normalize()) > 0.9999) {
                        result.addLine(startPos, endPos0);
                    } else {
                        result.addBezier(startPos, startDir, endPos0.subtract(startPos), endDir0.reverse());
                    }
                    i += j - 1;
                    continue a;
                }
                j--;
            }
            // No valid connection found, force connection
            Vec3 endPos = path0.get(i + 1);
            Vec3 endDir = path0.get(i + 1).subtract(path0.get(i)).multiply(1, 0, 1).normalize();
            if (startPos.y == endPos.y && startDir.dot(endDir) > 0.9999 && startDir.dot(endPos.subtract(startPos).normalize()) > 0.9999) {
                result.addLine(startPos, endPos);
            } else {
                result.addBezier(startPos, startDir, endPos.subtract(startPos), endDir.reverse());
            }
        }

        // Station end connection
        Vec3 pB = con.end().add(con.endDir().scale(30)).add(con.exitDir().reverse().scale(30));
        result.addBezier(last, lastDir, pB.subtract(last), con.exitDir().reverse());
        if (con.endDir().dot(con.exitDir().reverse()) > 0.999) {
            result.addLine(pB, con.end());
        } else {
            result.addBezier(pB, con.exitDir(), con.end().subtract(pB), con.endDir());
        }

        return result;
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

    /**
     * Validate track placement
     */
    public static boolean isValidTrackPlacement(
            Vec3 startPos,
            Vec3 startAxis,
            Vec3 endPos,
            Vec3 endAxis
    ) {
        // 1. Check distance (default max 100)
        double maxLength = 100.0;
        if (startPos.distanceToSqr(endPos) > maxLength * maxLength) {
            return false;
        }

        // 2. Check if same point
        if (startPos.equals(endPos)) {
            return false;
        }

        // 3. Normalize axes
        Vec3 normedAxis1 = startAxis.normalize();
        Vec3 normedAxis2 = endAxis.normalize();

        // 4. Check if parallel
        double[] intersect = VecHelper.intersect(startPos, endPos, normedAxis1, normedAxis2, Direction.Axis.Y);
        boolean parallel = intersect == null;

        // 5. Check perpendicular case
        if (parallel && normedAxis1.dot(normedAxis2) > 0) {
            return false;
        }

        // 6. Check turn angle
        if (!parallel) {
            double a1 = Mth.atan2(normedAxis2.z, normedAxis2.x);
            double a2 = Mth.atan2(normedAxis1.z, normedAxis1.x);
            double angle = a1 - a2;
            float absAngle = Math.abs(AngleHelper.deg(angle));

            if (absAngle < 60 || absAngle > 300) {
                return false;
            }

            intersect = VecHelper.intersect(startPos, endPos, normedAxis1, normedAxis2, Direction.Axis.Y);
            if (intersect == null || intersect[0] < 0 || intersect[1] < 0) {
                return false;
            }

            double dist1 = Math.abs(intersect[0]);
            double dist2 = Math.abs(intersect[1]);
            double turnSize = Math.min(dist1, dist2) - 0.1;

            boolean ninety = (absAngle + 0.25f) % 90 < 1;
            double minTurnSize = ninety ? 7 : 3.25;

            if (turnSize < minTurnSize) {
                return false;
            }
        }

        // 7. Check S-curve
        if (parallel) {
            Vec3 cross2 = normedAxis2.cross(new Vec3(0, 1, 0));
            double[] sTest = VecHelper.intersect(startPos, endPos, normedAxis1, cross2, Direction.Axis.Y);

            if (sTest != null && sTest[0] < 0) {
                return false;
            }

            if (sTest != null && !Mth.equal(Math.abs(sTest[1]), 0)) {
                double t = Math.abs(sTest[0]);
                double u = Math.abs(sTest[1]);
                double targetT = u <= 1 ? 3 : u * 2;

                if (t < targetT) {
                    return false;
                }
            }
        }

        return true;
    }

    public record ResultWay(
            CurveRoute.CompositeCurve way,
            List<TrackPutInfo> trackPutInfos
    ) {
        public void addLine(Vec3 start, Vec3 end) {
            way.addSegment(new CurveRoute.LineSegment(start, end));
            int n = Math.max((int) Math.abs(start.x - end.x), (int) Math.abs(start.z - end.z));
            for (int k = 0; k <= n; k++) {
                int x = (int) (start.x + MyMth.getSign(end.x - start.x) * k);
                int z = (int) (start.z + MyMth.getSign(end.z - start.z) * k);
                trackPutInfos.add(TrackPutInfo.getByDir(
                        new BlockPos(x, (int) start.y, z),
                        end.subtract(start),
                        null
                ));
            }
        }

        public void addBezier(Vec3 start, Vec3 startDir, Vec3 endOffset, Vec3 endDir) {
            if (Math.abs(startDir.dot(endDir)) > 0.9999 && startDir.dot(endOffset.normalize()) > 0.9999) {
                way.addSegment(new CurveRoute.LineSegment(start, start.add(endOffset)));
            } else {
                way.addSegment(CurveRoute.CubicBezier.getCubicBezier(start, startDir, endOffset, endDir));
            }
            trackPutInfos.add(TrackPutInfo.getByDir(
                    new BlockPos((int) start.x, (int) start.y, (int) start.z),
                    startDir,
                    new TrackPutInfo.BezierInfo(
                            start,
                            startDir,
                            endOffset,
                            endDir
                    )
            ));
        }
    }
}
