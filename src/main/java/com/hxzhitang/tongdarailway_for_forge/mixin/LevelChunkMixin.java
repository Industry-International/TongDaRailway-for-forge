package com.hxzhitang.tongdarailway_for_forge.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.TrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Redirect(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z")
    )
    private boolean hasBlockEntity$patchException(BlockState instance) {
        if (instance.is(AllBlocks.TRACK.get()) && !instance.getValue(TrackBlock.HAS_BE)) {
            return false;
        }
        return instance.hasBlockEntity();
    }

    @Inject(
            method = "promotePendingBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At(value = "HEAD"), cancellable = true
    )
    private void hasBlockEntity$patchException(BlockPos pos, CompoundTag tag, CallbackInfoReturnable<BlockEntity> cir) {
        BlockState state = this.getBlockState(pos);
        if (state.is(AllBlocks.TRACK.get()) && !state.getValue(TrackBlock.HAS_BE)) {
            cir.setReturnValue(null);
        }
    }
}
