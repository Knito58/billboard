package de.knito.billboard;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.billboard.block.BillboardEngineBlockEntity;

public class ModBlockEntities {
	public static final BlockEntityType<BillboardEngineBlockEntity> BILLBOARD_ENGINE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Billboard.id("billboard_engine"),
			FabricBlockEntityTypeBuilder.create(BillboardEngineBlockEntity::new, ModBlocks.BILLBOARD_ENGINE).build()
	);

	public static void initialize() {
	}
}
