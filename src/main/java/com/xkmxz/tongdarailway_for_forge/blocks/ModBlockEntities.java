package com.xkmxz.tongdarailway_for_forge.blocks;

import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Tongdarailway_for_forge.MODID);

    public static final RegistryObject<BlockEntityType<TrackSpawnerBlockEntity>> TRACK_SPAWNER =
            BLOCK_ENTITIES.register("track_spawner", // ① 注册名（ID）
                    () -> BlockEntityType.Builder.of(TrackSpawnerBlockEntity::new, ModBlocks.TRACK_SPAWNER.get()) // ② 延迟创建（Supplier）
                            .build(null)); // ⑤ 构建类型（null=不指定数据


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
