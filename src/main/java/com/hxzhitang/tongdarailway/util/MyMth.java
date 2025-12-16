package com.hxzhitang.tongdarailway.util;

import com.hxzhitang.tongdarailway.railway.RegionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import static com.hxzhitang.tongdarailway.Tongdarailway.CHUNK_GROUP_SIZE;

public class MyMth {
    /**
     * Get chunk X position from region position and chunk index
     * @param regionPos region position
     * @param chunkIndexX chunk index within region
     * @return chunk X position
     */
    public static int chunkPosXFromRegionPos(RegionPos regionPos, int chunkIndexX) {
        return regionPos.x() * CHUNK_GROUP_SIZE + chunkIndexX;
    }

    /**
     * Get chunk Z position from region position and chunk index
     * @param regionPos region position
     * @param chunkIndexZ chunk index within region
     * @return chunk Z position
     */
    public static int chunkPosZFromRegionPos(RegionPos regionPos, int chunkIndexZ) {
        return regionPos.z() * CHUNK_GROUP_SIZE + chunkIndexZ;
    }

    /**
     * Get region position from chunk position
     * @param chunkPos chunk position
     * @return region position
     */
    public static RegionPos regionPosFromChunkPos(ChunkPos chunkPos) {
        return new RegionPos(Math.floorDiv(chunkPos.x, CHUNK_GROUP_SIZE), Math.floorDiv(chunkPos.z, CHUNK_GROUP_SIZE));
    }

    public static ChunkPos getChunkPos(int x, int z) {
        return new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    public static Vec3 inRegionPos2WorldPos(RegionPos regionPos, Vec3 vec3) {
        return vec3.add(new Vec3(regionPos.x() * CHUNK_GROUP_SIZE * 16, 0, regionPos.z() * CHUNK_GROUP_SIZE * 16));
    }

    public static int getSign(double number) {
        return number > 0 ? 1 : (number < 0 ? -1 : 0);
    }
}
