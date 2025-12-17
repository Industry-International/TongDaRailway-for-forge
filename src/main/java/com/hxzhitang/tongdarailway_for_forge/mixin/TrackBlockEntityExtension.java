package com.hxzhitang.tongdarailway_for_forge.mixin;

import com.hxzhitang.tongdarailway_for_forge.blocks.ITrackPreGenExtension;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlockEntityTilt;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TrackBlockEntity.class, remap = false)
public abstract class TrackBlockEntityExtension extends SmartBlockEntity implements ITrackPreGenExtension {

    @Shadow(remap = false)
    public abstract void addConnection(BezierConnection connection);

    @Shadow(remap = false)
    public TrackBlockEntityTilt tilt;

    public TrackBlockEntityExtension(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    List<BezierConnection> tongDaRailway$preConnections = new ArrayList<>();

    @Unique
    @Override
    public void addConnectionToPreGen(BezierConnection connection) {
        tongDaRailway$preConnections.add(connection);
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void tick$addConnection(CallbackInfo ci) {
        if(!tongDaRailway$preConnections.isEmpty() && !level.isClientSide()){
            tongDaRailway$preConnections.forEach(this::addConnection);
            tilt.tryApplySmoothing();
            tongDaRailway$preConnections.clear();
        }
    }
}
