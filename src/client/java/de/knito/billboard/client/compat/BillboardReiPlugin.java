package de.knito.billboard.client.compat;

import java.util.stream.Stream;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.client.BillboardEngineScreen;

/** REI support: drag a block from the REI list onto the engine's pixel slot. Only loaded when REI is installed. */
public class BillboardReiPlugin implements REIClientPlugin {
	@Override
	public void registerScreens(ScreenRegistry registry) {
		registry.registerDraggableStackVisitor(new DraggableStackVisitor<BillboardEngineScreen>() {
			@Override
			public <R extends Screen> boolean isHandingScreen(R screen) {
				return screen instanceof BillboardEngineScreen;
			}

			@Override
			public DraggedAcceptorResult acceptDraggedStack(DraggingContext<BillboardEngineScreen> context, DraggableStack stack) {
				ItemStack item = pixelOf(stack.getStack());
				BillboardEngineScreen screen = context.getScreen();
				Rect2i area = screen.pixelSlotArea();
				if (item.isEmpty() || !area.contains(context.getCurrentPosition().x, context.getCurrentPosition().y)) {
					return DraggedAcceptorResult.PASS;
				}
				screen.choosePixel(item);
				return DraggedAcceptorResult.ACCEPTED;
			}

			@Override
			public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<BillboardEngineScreen> context, DraggableStack stack) {
				if (pixelOf(stack.getStack()).isEmpty()) {
					return Stream.empty();
				}
				Rect2i area = context.getScreen().pixelSlotArea();
				return Stream.of(BoundsProvider.ofRectangle(new Rectangle(area.getX(), area.getY(), area.getWidth(), area.getHeight())));
			}
		});
	}

	private static ItemStack pixelOf(EntryStack<?> entry) {
		if (entry.getType() != VanillaEntryTypes.ITEM) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = entry.castValue();
		return BillboardEngineBlockEntity.isValidPixel(stack) ? stack : ItemStack.EMPTY;
	}
}
