package com.xkmxz.tongdarailway_for_forge.util;

import com.xkmxz.tongdarailway_for_forge.railway.RegionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import static com.xkmxz.tongdarailway_for_forge.Config.chunkGroupSize;

public class MyMth {

    // ========== 原有方法 ==========
    public static int chunkPosXFromRegionPos(RegionPos regionPos, int chunkIndexX) {
        return regionPos.x() * chunkGroupSize + chunkIndexX;
    }

    public static int chunkPosZFromRegionPos(RegionPos regionPos, int chunkIndexZ) {
        return regionPos.z() * chunkGroupSize + chunkIndexZ;
    }

    public static RegionPos regionPosFromChunkPos(ChunkPos chunkPos) {
        return new RegionPos(Math.floorDiv(chunkPos.x, chunkGroupSize), Math.floorDiv(chunkPos.z, chunkGroupSize));
    }

    public static ChunkPos getChunkPos(int x, int z) {
        return new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    public static Vec3 inRegionPos2WorldPos(RegionPos regionPos, Vec3 vec3) {
        return vec3.add(new Vec3(regionPos.x() * chunkGroupSize * 16, 0, regionPos.z() * chunkGroupSize * 16));
    }

    public static int getSign(double number) {
        return number > 0 ? 1 : (number < 0 ? -1 : 0);
    }

    // ========== 新增辅助方法（用于贝塞尔曲线计算） ==========
    public static Vec3 getCurveStart(BlockPos pos, Vec3 dir) {
        // 返回方块中心 + 方向向量的一半（半个方块偏移）
        return Vec3.atCenterOf(pos).add(dir.scale(0.5));
    }

    public static Vec3 myCeil(Vec3 vec) {
        return new Vec3(Math.ceil(vec.x), Math.ceil(vec.y), Math.ceil(vec.z));
    }

    public static Vec3 get8Dir(Vec3 vec) {
        double angle = Math.atan2(vec.z, vec.x);
        double deg = Math.toDegrees(angle);
        double rounded = Math.round(deg / 45.0) * 45.0;
        double rad = Math.toRadians(rounded);
        return new Vec3(Math.cos(rad), 0, Math.sin(rad)).normalize();
    }

    public static Vec3 rotateAroundY(Vec3 vec, double cross, int degrees) {
        double angleRad = Math.toRadians(degrees);
        if (cross < 0) angleRad = -angleRad;
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = vec.x * cos - vec.z * sin;
        double z = vec.x * sin + vec.z * cos;
        return new Vec3(x, vec.y, z).normalize();
    }
}

