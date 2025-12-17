package com.hxzhitang.tongdarailway_for_forge.datagen;

import com.hxzhitang.tongdarailway_for_forge.Tongdarailway_for_forge;
import com.hxzhitang.tongdarailway_for_forge.blocks.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class ModLanguageProviderZHCN extends LanguageProvider {
    public ModLanguageProviderZHCN(PackOutput output) {
        super(output, Tongdarailway_for_forge.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        this.add(ModBlocks.TRACK_SPAWNER.get(), "轨道刷新器");
    }
}
