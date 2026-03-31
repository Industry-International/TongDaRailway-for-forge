package com.xkmxz.tongdarailway_for_forge.datagen;

import com.xkmxz.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.xkmxz.tongdarailway_for_forge.blocks.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class ModLanguageProviderENUS extends LanguageProvider {
    public ModLanguageProviderENUS(PackOutput output) {
        super(output, Tongdarailway_for_forge.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add(ModBlocks.TRACK_SPAWNER.get(), "Track Spawner");
    }
}
