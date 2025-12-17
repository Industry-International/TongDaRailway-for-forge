package com.hxzhitang.tongdarailway_for_forge.command;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.hxzhitang.tongdarailway_for_forge.railway.RailwayBuilder;
import com.hxzhitang.tongdarailway_for_forge.railway.RailwayMap;
import com.hxzhitang.tongdarailway_for_forge.railway.RegionPos;
import com.hxzhitang.tongdarailway_for_forge.railway.planner.StationPlanner;
import com.hxzhitang.tongdarailway_for_forge.structure.RoadbedManager;
import com.hxzhitang.tongdarailway_for_forge.structure.StationManager;
import com.hxzhitang.tongdarailway_for_forge.structure.StationStructure;
import com.hxzhitang.tongdarailway_for_forge.util.MyMth;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Test command for TongDaRailway mod
 * Usage:
 *   /tongda test station - Spawn a random normal station at player's position
 *   /tongda test underground - Spawn a random underground station at player's position
 *   /tongda info - Show loaded structures info
 */
public class TongdaTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tongda")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test")
                    .then(Commands.literal("station")
                        .executes(TongdaTestCommand::spawnNormalStation))
                    .then(Commands.literal("underground")
                        .executes(TongdaTestCommand::spawnUndergroundStation)))
                .then(Commands.literal("info")
                    .executes(TongdaTestCommand::showInfo))
                .then(Commands.literal("region")
                    .executes(TongdaTestCommand::showRegionStatus))
                .then(Commands.literal("goto")
                    .executes(TongdaTestCommand::teleportToNearestStation))
        );
    }

    private static int spawnNormalStation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (StationManager.normalStation.isEmpty()) {
            source.sendFailure(Component.literal("No normal stations loaded!"));
            return 0;
        }

        try {
            ServerLevel level = source.getLevel();
            BlockPos playerPos = BlockPos.containing(source.getPosition());
            long seed = System.currentTimeMillis();

            StationStructure station = StationManager.getRandomNormalStation(seed);
            if (station == null) {
                source.sendFailure(Component.literal("Failed to get random station!"));
                return 0;
            }

            // Place the station segments around the player position
            ChunkPos centerChunk = new ChunkPos(playerPos);
            int radius = 3; // Place 3x3 chunk area

            for (int cx = -radius; cx <= radius; cx++) {
                for (int cz = -radius; cz <= radius; cz++) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + cx, centerChunk.z + cz);
                    station.putSegment(level, chunkPos, playerPos);
                }
            }

            source.sendSuccess(() -> Component.literal(
                "Spawned normal station (ID: " + station.getId() + ") at " + playerPos.toShortString() +
                "\nExits: " + station.getExitCount()
            ), true);

            Tongdarailway_for_forge.LOGGER.info("Test command spawned normal station at {}", playerPos);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning station: " + e.getMessage()));
            Tongdarailway_for_forge.LOGGER.error("Error spawning station", e);
            return 0;
        }
    }

    private static int spawnUndergroundStation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (StationManager.undergroundStation.isEmpty()) {
            source.sendFailure(Component.literal("No underground stations loaded!"));
            return 0;
        }

        try {
            ServerLevel level = source.getLevel();
            BlockPos playerPos = BlockPos.containing(source.getPosition());
            long seed = System.currentTimeMillis();

            StationStructure station = StationManager.getRandomUnderGroundStation(seed);
            if (station == null) {
                source.sendFailure(Component.literal("Failed to get random underground station!"));
                return 0;
            }

            // Place the station segments around the player position
            ChunkPos centerChunk = new ChunkPos(playerPos);
            int radius = 3;

            for (int cx = -radius; cx <= radius; cx++) {
                for (int cz = -radius; cz <= radius; cz++) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + cx, centerChunk.z + cz);
                    station.putSegment(level, chunkPos, playerPos);
                }
            }

            source.sendSuccess(() -> Component.literal(
                "Spawned underground station (ID: " + station.getId() + ") at " + playerPos.toShortString() +
                "\nExits: " + station.getExitCount()
            ), true);

            Tongdarailway_for_forge.LOGGER.info("Test command spawned underground station at {}", playerPos);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning station: " + e.getMessage()));
            Tongdarailway_for_forge.LOGGER.error("Error spawning station", e);
            return 0;
        }
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int normalCount = StationManager.normalStation.size();
        int undergroundCount = StationManager.undergroundStation.size();
        int railwayCount = RoadbedManager.ground.size() + RoadbedManager.tunnel.size() + RoadbedManager.bridge.size();

        String info = String.format(
            "=== TongDaRailway Info ===\n" +
            "Normal Stations: %d\n" +
            "Underground Stations: %d\n" +
            "Railway Templates: %d\n" +
            "=========================",
            normalCount, undergroundCount, railwayCount
        );

        source.sendSuccess(() -> Component.literal(info), false);

        // List station IDs
        if (!StationManager.normalStation.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Normal Station IDs: " + StationManager.normalStation.keySet()), false);
        }
        if (!StationManager.undergroundStation.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Underground Station IDs: " + StationManager.undergroundStation.keySet()), false);
        }

        return 1;
    }

    private static int showRegionStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(playerPos);
        RegionPos regionPos = MyMth.regionPosFromChunkPos(chunkPos);

        source.sendSuccess(() -> Component.literal(
            "=== Region Status ===\n" +
            "Player Pos: " + playerPos.toShortString() + "\n" +
            "Chunk: " + chunkPos + "\n" +
            "Region: " + regionPos
        ), false);

        RailwayBuilder builder = RailwayBuilder.getInstance(source.getLevel().getSeed());
        if (builder == null) {
            source.sendFailure(Component.literal("RailwayBuilder not initialized! Try exploring new chunks first."));
            return 0;
        }

        RailwayMap railwayMap = builder.regionRailways.get(regionPos);
        if (railwayMap == null) {
            source.sendFailure(Component.literal("No railway data for this region! Region may not be generated yet."));
            return 0;
        }

        int stationCount = railwayMap.stations.size();
        int routeChunks = railwayMap.routeMap.size();
        int trackChunks = railwayMap.trackMap.size();

        source.sendSuccess(() -> Component.literal(
            "Stations planned: " + stationCount + "\n" +
            "Route chunks: " + routeChunks + "\n" +
            "Track chunks: " + trackChunks
        ), false);

        // Show station positions
        if (!railwayMap.stations.isEmpty()) {
            source.sendSuccess(() -> Component.literal("--- Station Positions ---"), false);
            for (StationPlanner.StationGenInfo station : railwayMap.stations) {
                BlockPos pos = station.placePos();
                String type = station.stationStructure() != null ?
                    station.stationStructure().getType().name() : "UNKNOWN";
                source.sendSuccess(() -> Component.literal(
                    type + ": " + pos.toShortString() + " (distance: " +
                    (int)Math.sqrt(playerPos.distSqr(pos)) + " blocks)"
                ), false);
            }
        }

        return 1;
    }

    private static int teleportToNearestStation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(playerPos);
        RegionPos regionPos = MyMth.regionPosFromChunkPos(chunkPos);

        RailwayBuilder builder = RailwayBuilder.getInstance(source.getLevel().getSeed());
        if (builder == null) {
            source.sendFailure(Component.literal("RailwayBuilder not initialized!"));
            return 0;
        }

        // Search in current and adjacent regions
        BlockPos nearestStation = null;
        double nearestDist = Double.MAX_VALUE;

        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -1; rz <= 1; rz++) {
                RegionPos searchRegion = new RegionPos(regionPos.x() + rx, regionPos.z() + rz);
                RailwayMap railwayMap = builder.regionRailways.get(searchRegion);
                if (railwayMap == null) continue;

                for (StationPlanner.StationGenInfo station : railwayMap.stations) {
                    BlockPos stationPos = station.placePos();
                    double dist = playerPos.distSqr(stationPos);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestStation = stationPos;
                    }
                }
            }
        }

        if (nearestStation == null) {
            source.sendFailure(Component.literal("No stations found in nearby regions!"));
            return 0;
        }

        final BlockPos targetPos = nearestStation;
        try {
            source.getEntityOrException().teleportTo(targetPos.getX() + 0.5, targetPos.getY() + 5, targetPos.getZ() + 0.5);
            source.sendSuccess(() -> Component.literal("Teleported to station at " + targetPos.toShortString()), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to teleport: " + e.getMessage()));
            return 0;
        }
    }
}
