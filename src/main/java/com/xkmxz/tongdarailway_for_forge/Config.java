package com.xkmxz.tongdarailway_for_forge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Tongdarailway_for_forge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLE_TRACK_SPAWNER = BUILDER
            .comment("Can the track spawner work")
            .define("enableTrackSpawner", true);

    private static final ForgeConfigSpec.BooleanValue GENERATE_TRACK_SPAWNER = BUILDER
            .comment("Can the track spawner be generated")
            .define("generateTrackSpawner", true);

    private static final ForgeConfigSpec.BooleanValue PLACE_TRACKS_USING_TRACK_SPAWNER = BUILDER
            .comment("Use the track spawner,(true) or place rails during world generation.(false)")
            .define("placeTracksUsingTrackSpawner", true);
    // 新增配置项
    private static final ForgeConfigSpec.IntValue CHUNK_GROUP_SIZE = BUILDER
            .comment("Size of a railway planning region in chunks")
            .defineInRange("chunkGroupSize", 128, 16, 512);

    private static final ForgeConfigSpec.IntValue HEIGHT_MAX_INCREMENT = BUILDER
            .comment("Maximum height increment for railway elevation changes")
            .defineInRange("heightMaxIncrement", 100, 10, 500);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enableTrackSpawner;
    public static boolean generateTrackSpawner;
    public static boolean useTrackSpawnerPlaceTrack;

    // 新增公共静态变量
    // ✅ 修正：给默认值，不在此处调用 .get()
    public static int chunkGroupSize = 128;
    public static int heightMaxIncrement = 100;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enableTrackSpawner = ENABLE_TRACK_SPAWNER.get();
        generateTrackSpawner = GENERATE_TRACK_SPAWNER.get();
        useTrackSpawnerPlaceTrack = PLACE_TRACKS_USING_TRACK_SPAWNER.get();
        chunkGroupSize = CHUNK_GROUP_SIZE.get();
        heightMaxIncrement = HEIGHT_MAX_INCREMENT.get();
    }
}
