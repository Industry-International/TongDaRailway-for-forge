package com.xkmxz.tongdarailway_for_forge.worldgen;

import com.xkmxz.tongdarailway_for_forge.Config;
import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.xkmxz.tongdarailway_for_forge.blocks.ITrackPreGenExtension;
import com.xkmxz.tongdarailway_for_forge.blocks.ModBlocks;
import com.xkmxz.tongdarailway_for_forge.blocks.TrackSpawnerBlockEntity;
import com.xkmxz.tongdarailway_for_forge.railway.RailwayBuilder;
import com.xkmxz.tongdarailway_for_forge.railway.RailwayMap;
import com.xkmxz.tongdarailway_for_forge.railway.RegionPos;
import com.xkmxz.tongdarailway_for_forge.railway.planner.StationPlanner;
import com.xkmxz.tongdarailway_for_forge.structure.RailwayTemplate;
import com.xkmxz.tongdarailway_for_forge.structure.RoadbedManager;
import com.xkmxz.tongdarailway_for_forge.structure.TrackPutInfo;
import com.xkmxz.tongdarailway_for_forge.util.CurveRoute;
import com.xkmxz.tongdarailway_for_forge.util.MyMth;
import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RailwayFeature extends Feature<RailwayFeatureConfig> {
    public RailwayFeature(Codec<RailwayFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<RailwayFeatureConfig> ctx) {
        ChunkPos cPos = new ChunkPos(ctx.origin());
        RegionPos regionPos = MyMth.regionPosFromChunkPos(cPos);
        WorldGenLevel world = ctx.level();
        ChunkAccess chunk = world.getChunk(cPos.x, cPos.z);

        RailwayBuilder builder = RailwayBuilder.getInstance(ctx.level().getSeed());
        if (builder == null) return false;

        RailwayMap railwayMap = builder.regionRailways.get(regionPos);
        if (railwayMap == null) return false;

        if (builder.regionRailways.containsKey(regionPos)) {
            if (railwayMap.routeMap.containsKey(cPos)) {
                placeRoadbed(railwayMap, cPos, chunk, world);
            }
        }

        for (StationPlanner.StationGenInfo stationPlace : railwayMap.stations) {
            var station = stationPlace.stationStructure();
            if (station == null) continue;
            var pos = stationPlace.placePos();

            station.putSegment(world, cPos, pos);
        }

        if (Config.useTrackSpawnerPlaceTrack && Config.generateTrackSpawner) {
                if (builder.regionRailways.containsKey(regionPos)) {
                    if (railwayMap.trackMap.containsKey(cPos)) {
                        var trackList = railwayMap.trackMap.get(cPos);
                        var firstInfo = trackList.get(0);
                        BlockPos checkPos = firstInfo.pos().offset(0, -1, 0);
                        if (!world.getBlockState(checkPos).is(ModBlocks.TRACK_SPAWNER.get())) {
                            world.setBlock(checkPos, ModBlocks.TRACK_SPAWNER.get().defaultBlockState(), 3);
                        }
                        if (world.getBlockEntity(checkPos) instanceof TrackSpawnerBlockEntity trackSpawner) {
                            trackSpawner.addTrackPutInfo(railwayMap.trackMap.get(cPos));
                        }
                    }
                }
        }
        if (!Config.useTrackSpawnerPlaceTrack) {
            if (builder.regionRailways.containsKey(regionPos) && railwayMap.trackMap.containsKey(cPos)) {
                List<TrackPutInfo> tracks = railwayMap.trackMap.get(cPos);
                tracks.forEach(track -> {
                    if (track.bezier() != null) {
                        if (Math.abs(track.bezier().endOffset().y) > 15){
                            Tongdarailway_for_forge.LOGGER.warn("Railway track height offset is too large. Generation Failed at" + track.pos().toString());
                            return;
                        }
                        placeCurveTrack(world, track);
                    } else {
                        if (!world.getBlockState(track.pos()).is(AllBlocks.TRACK.get())) {
                            world.setBlock(track.pos(), AllBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.SHAPE, track.shape()), 3);
                        }
                    }
                });
            }
        }

        return true;
    }

    private void placeCurveTrack(WorldGenLevel world, TrackPutInfo track) {
        BlockPos startPos = track.pos();
        world.setBlock(startPos, AllBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.SHAPE, track.shape()).setValue(TrackBlock.HAS_BE,true), 3);

        Vec3 offset = track.bezier().endOffset();
        BlockPos endPos = startPos.offset((int) offset.x, (int) offset.y, (int) offset.z);
        world.setBlock(endPos, AllBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.SHAPE, track.endShape()).setValue(TrackBlock.HAS_BE,true), 3);

        Vec3 start1 = track.bezier().start().add(getStartVec(track.bezier().startAxis()));
        Vec3 start2 = track.bezier().start().add(track.bezier().endOffset()).add(getStartVec(track.bezier().endAxis()));

        Vec3 axis1 = track.bezier().startAxis();
        Vec3 axis2 = track.bezier().endAxis();

        Vec3 normal1 = new Vec3(0, 1, 0);
        Vec3 normal2 = new Vec3(0, 1, 0);

        BezierConnection connection = new BezierConnection(
                Couple.create(startPos, endPos),
                Couple.create(start1, start2),
                Couple.create(axis1, axis2),
                Couple.create(normal1, normal2),
                true,
                false,
                TrackMaterial.ANDESITE
        );

        var tbe1 = world.getBlockEntity(startPos);
        var tbe2 = world.getBlockEntity(endPos);

        if(tbe1 != null && tbe2 != null){
            ((ITrackPreGenExtension) tbe1).addConnectionToPreGen(connection);
            ((ITrackPreGenExtension) tbe2).addConnectionToPreGen(connection.secondary());
        }
    }

    private static Vec3 getStartVec(Vec3 dir) {
        double offX;
        if (Math.abs(dir.x) < 1e-6) {
            offX = 0.5;
        } else if (dir.x < 0) {
            offX = 0;
        } else {
            offX = 1;
        }

        double offZ;
        if (Math.abs(dir.z) < 1e-6) {
            offZ = 0.5;
        } else if (dir.z < 0) {
            offZ = 0;
        } else {
            offZ = 1;
        }

        return new Vec3(offX, 0, offZ);
    }

    /**
     * 放置路基结构
     * 根据地形高度和水域情况选择合适的路基模板(地面、桥梁或隧道)
     * @param railwayMap 铁路地图
     * @param cPos 区块位置
     * @param chunk 区块访问对象
     * @param world 世界生成级别
     */
    private static void placeRoadbed(RailwayMap railwayMap, ChunkPos cPos, ChunkAccess chunk, WorldGenLevel world) {
        var routes = railwayMap.routeMap.get(cPos);
        for (CurveRoute.CompositeCurve route : routes) {
            // 使用路径哈希值作为种子，确保模板选择的一致性，解决桥梁连接问题
            long routeSeed = route.hashCode();
            long groundSeed = routeSeed + 12345;
            long bridgeSeed = routeSeed + 67890;
            long tunnelSeed = routeSeed + 13579;
            
            RailwayTemplate ground = RoadbedManager.getRandomGround((int) groundSeed);
            RailwayTemplate bridge = RoadbedManager.getRandomBridge((int) bridgeSeed);
            RailwayTemplate tunnel = RoadbedManager.getRandomTunnel((int) tunnelSeed);
            var testPoint = new CurveRoute.Point3D(cPos.x*16+8, 80, cPos.z*16+8);
            CurveRoute.NearestPointResult result0 = route.findNearestPoint(testPoint);

            var nearestPoint0 = result0.nearestPoint;
            var frame0 = result0.frame;
            var normal0 = frame0.getVerticalXZNormal();

            double x0 = nearestPoint0.x, y0 = nearestPoint0.y, z0 = nearestPoint0.z;
            double A = normal0.x, B = normal0.y+1E-5, C = normal0.z;
            double D = -(A*x0 + B*y0 + C*z0);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = cPos.x*16 + x;
                    int wz = cPos.z*16 + z;
                    double y = -(A/B)*wx - (C/B)*wz - (D/B);
                    var worldPoint = new CurveRoute.Point3D(wx, y, wz);
                    var result = route.findNearestPoint(worldPoint);
                    var nearest = result.nearestPoint;
                    var frame = CurveRoute.adjustmentFrame(result.frame);

                    double t = route.getGlobalParameter(result.segmentIndex, result.parameter);

                    BlockPos nearestPos = new BlockPos((int) nearest.x, (int) nearest.y, (int) nearest.z);
                    int h = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, nearestPos.getX(), nearestPos.getZ());

                    // 检测是否为水域 - 检查当前位置和海平面位置是否有水
                    boolean isWater = checkIsWater(world, nearestPos, h);
                    
                    // 判断使用哪种结构模板
                    boolean conditionBridge = nearest.y > h + 10 || isWater; // 高度差大或为水域时使用桥梁
                    boolean conditionTunnel = nearest.y < h - 9;

                    RailwayTemplate structureTemplate;
                    if (conditionBridge) {
                        structureTemplate = bridge; // 使用桥梁模板跨越水域或高度差
                    } else if (conditionTunnel) {
                        structureTemplate = tunnel; // 使用隧道模板穿越山体
                    } else {
                        structureTemplate = ground; // 使用地面路基模板
                    }

                    for (int oy = structureTemplate.getLowerBound(); oy <= structureTemplate.getUpperBound(); oy++) {
                        double y1 = y + oy;

                        var worldPoint1 = new CurveRoute.Point3D(wx, y1, wz);
                        var vec = worldPoint1.subtract(nearest);

                        if (Math.abs(vec.dot(frame.tangent)) > 3)
                            continue;

                        double localX = t * route.getTotalLength();
                        double localY = vec.dot(frame.normal);
                        double localZ = vec.dot(frame.binormal);

                        BlockState blockState = structureTemplate.getBlockState(localX, localY, localZ);
                        if (blockState != null) {
                            BlockPos blockPos = new BlockPos(x, (int) Math.round(y1), z);
                            chunk.setBlockState(blockPos, blockState, true);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 检查指定位置是否为水域
     * 检测海平面高度和地面高度是否有水
     * @param world 世界生成级别
     * @param pos 位置
     * @param groundHeight 地面高度
     * @return 是否为水域
     */
    private static boolean checkIsWater(WorldGenLevel world, BlockPos pos, int groundHeight) {
        // 检查地面位置是否有水
        BlockPos groundPos = new BlockPos(pos.getX(), groundHeight, pos.getZ());
        BlockState groundState = world.getBlockState(groundPos);
        if (groundState.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
            return true;
        }
        
        // 检查海平面位置是否有水
        int seaLevel = 63; // Minecraft默认海平面高度
        BlockPos seaPos = new BlockPos(pos.getX(), seaLevel, pos.getZ());
        BlockState seaState = world.getBlockState(seaPos);
        if (seaState.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
            return true;
        }
        
        // 检查周围3格范围内是否有水
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos nearbyPos = new BlockPos(pos.getX() + dx, groundHeight, pos.getZ() + dz);
                BlockState nearbyState = world.getBlockState(nearbyPos);
                if (nearbyState.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
