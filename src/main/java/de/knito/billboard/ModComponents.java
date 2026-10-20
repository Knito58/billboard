package de.knito.billboard;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import de.knito.billboard.item.WandCorners;

public class ModComponents {
	public static final DataComponentType<WandCorners> WAND_CORNERS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Billboard.id("wand_corners"),
			DataComponentType.<WandCorners>builder()
					.persistent(WandCorners.CODEC)
					.networkSynchronized(WandCorners.STREAM_CODEC)
					.build()
	);

	public static void initialize() {
	}
}
