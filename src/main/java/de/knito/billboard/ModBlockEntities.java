package de.knito.billboard;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.script.ScriptBoxBlockEntity;

public class ModBlockEntities {
	public static final BlockEntityType<BillboardEngineBlockEntity> BILLBOARD_ENGINE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Billboard.id("billboard_engine"),
			FabricBlockEntityTypeBuilder.create(BillboardEngineBlockEntity::new, ModBlocks.BILLBOARD_ENGINE).build()
	);

	public static final BlockEntityType<ScriptBoxBlockEntity> SCRIPT_BOX = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Billboard.id("script_box"),
			FabricBlockEntityTypeBuilder.create(ScriptBoxBlockEntity::new, ModBlocks.SCRIPT_BOX).build()
	);

	public static void initialize() {
	}
}
