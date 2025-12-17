package com.hxzhitang.tongdarailway_for_forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionMixin {
    @Redirect(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/WorldGenRegion;ensureCanWrite(Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean bypassExpensiveCalculationIfNecessary(WorldGenRegion instance, BlockPos blockPos) {
        // Always return true to bypass the expensive calculation for track blocks
        // This allows track placement during world generation
        return true;
    }
}
