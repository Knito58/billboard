package de.knito.billboard.client.compat;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import de.knito.billboard.Billboard;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.client.BillboardEngineScreen;

/** JEI support: drag a block from the JEI list onto the engine's pixel slot. Only loaded when JEI is installed. */
public class BillboardJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Billboard.id("jei");
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGhostIngredientHandler(BillboardEngineScreen.class, new IGhostIngredientHandler<>() {
			@Override
			public <I> List<Target<I>> getTargetsTyped(BillboardEngineScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
				Optional<ItemStack> stack = ingredient.getItemStack();
				if (stack.isEmpty() || !BillboardEngineBlockEntity.isValidPixel(stack.get())) {
					return List.of();
				}
				Rect2i area = screen.pixelSlotArea();
				return List.of(new Target<>() {
					@Override
					public Rect2i getArea() {
						return area;
					}

					@Override
					public void accept(I dropped) {
						screen.choosePixel(stack.get());
					}
				});
			}

			@Override
			public void onComplete() {
			}
		});
	}
}
