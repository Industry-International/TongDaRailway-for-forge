package com.hxzhitang.tongdarailway;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Tongdarailway.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
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

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enableTrackSpawner;
    public static boolean generateTrackSpawner;
    public static boolean useTrackSpawnerPlaceTrack;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enableTrackSpawner = ENABLE_TRACK_SPAWNER.get();
        generateTrackSpawner = GENERATE_TRACK_SPAWNER.get();
        useTrackSpawnerPlaceTrack = PLACE_TRACKS_USING_TRACK_SPAWNER.get();
    }
}
