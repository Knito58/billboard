package de.knito.billboard.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.billboard.Billboard;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.menu.BillboardEngineMenu;
import de.knito.billboard.net.EngineActionPayload;
import de.knito.billboard.wall.BillboardFont;

public class BillboardEngineScreen extends AbstractContainerScreen<BillboardEngineMenu> {
	private static final Identifier BACKGROUND = Billboard.id("textures/gui/billboard_engine.png");
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int STATUS_Y = 108;

	private EditBox textBox;
	private int speed;
	private int size;
	private Component status = Component.empty();

	public BillboardEngineScreen(BillboardEngineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 212);
		this.inventoryLabelY = BillboardEngineMenu.INVENTORY_Y - 11;
		this.speed = -1;
		this.size = -1;
	}

	@Override
	protected void init() {
		super.init();
		String oldText = textBox != null ? textBox.getValue() : menu.initialText();

		textBox = new EditBox(font, leftPos + 8, topPos + 18, 160, 18, Component.translatable("gui.billboard.text"));
		textBox.setMaxLength(BillboardEngineBlockEntity.MAX_TEXT_LENGTH);
		textBox.setHint(Component.translatable("gui.billboard.text_hint"));
		textBox.setValue(oldText);
		addRenderableWidget(textBox);

		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeSpeed(-1))
				.bounds(leftPos + 56, topPos + 41, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeSpeed(1))
				.bounds(leftPos + 152, topPos + 41, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeSize(-1))
				.bounds(leftPos + 56, topPos + 63, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeSize(1))
				.bounds(leftPos + 152, topPos + 63, 16, 18).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.billboard.go"), b -> send(EngineActionPayload.Action.GO))
				.bounds(leftPos + 8, topPos + 86, 50, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.billboard.stop"), b -> send(EngineActionPayload.Action.STOP))
				.bounds(leftPos + 63, topPos + 86, 50, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.billboard.restore"), b -> send(EngineActionPayload.Action.RESTORE))
				.bounds(leftPos + 118, topPos + 86, 50, 18).build());
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(textBox);
	}

	private int currentSpeed() {
		// Until the player touches the buttons, show what the engine has stored.
		return speed < 0 ? Math.clamp(menu.speed(), BillboardEngineBlockEntity.MIN_SPEED, BillboardEngineBlockEntity.MAX_SPEED) : speed;
	}

	private void changeSpeed(int delta) {
		speed = Math.clamp(currentSpeed() + delta, BillboardEngineBlockEntity.MIN_SPEED, BillboardEngineBlockEntity.MAX_SPEED);
		send(EngineActionPayload.Action.SAVE);
	}

	private int currentSize() {
		return size < 0 ? Math.clamp(menu.size(), BillboardFont.MIN_SIZE, BillboardFont.MAX_SIZE) : size;
	}

	private void changeSize(int delta) {
		size = Math.clamp(currentSize() + delta, BillboardFont.MIN_SIZE, BillboardFont.MAX_SIZE);
		send(EngineActionPayload.Action.SAVE);
	}

	private void send(EngineActionPayload.Action action) {
		ClientPlayNetworking.send(new EngineActionPayload(action, textBox.getValue(), currentSpeed(), currentSize()));
	}

	public void setStatus(Component message) {
		status = message;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return super.keyPressed(event);
		}
		// While typing, letters like E must go into the text box instead of closing the screen.
		if (textBox.isFocused() && (textBox.keyPressed(event) || textBox.canConsumeInput())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		// Keep what was typed even if no button was pressed; must go out before the menu closes on the server.
		send(EngineActionPayload.Action.SAVE);
		super.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.centeredText(font, Component.translatable("gui.billboard.speed", currentSpeed()), 112, 46, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("gui.billboard.size", currentSize()), 112, 68, 0xFFFFFFFF);

		Component state;
		int stateColor;
		if (menu.isShowing() && menu.isRunning()) {
			state = Component.translatable("gui.billboard.state_running");
			stateColor = 0xFF207020;
		} else if (menu.isShowing()) {
			state = Component.translatable("gui.billboard.state_stopped");
			stateColor = 0xFF806000;
		} else {
			state = Component.translatable("gui.billboard.state_idle");
			stateColor = LABEL_COLOR;
		}
		graphics.text(font, state, imageWidth - 8 - font.width(state), titleLabelY, stateColor, false);

		if (!status.getString().isEmpty()) {
			var lines = font.split(status, imageWidth - 16);
			if (!lines.isEmpty()) {
				graphics.text(font, lines.getFirst(), 8, STATUS_Y, 0xFF802020, false);
			}
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
			if (hoveredSlot.index == BillboardEngineBlockEntity.SLOT_WAND) {
				graphics.setTooltipForNextFrame(Component.translatable("gui.billboard.slot_wand"), mouseX, mouseY);
			} else if (hoveredSlot.index == BillboardEngineBlockEntity.SLOT_PIXEL) {
				graphics.setTooltipForNextFrame(Component.translatable("gui.billboard.slot_pixel"), mouseX, mouseY);
			}
		}
		if (mouseX >= leftPos + 73 && mouseX < leftPos + 152 && mouseY >= topPos + 63 && mouseY < topPos + 81) {
			graphics.setTooltipForNextFrame(Component.translatable("gui.billboard.size_tooltip"), mouseX, mouseY);
		}
		if (!status.getString().isEmpty() && mouseY >= topPos + STATUS_Y && mouseY < topPos + STATUS_Y + 9
				&& font.width(status) > imageWidth - 16) {
			graphics.setTooltipForNextFrame(status, mouseX, mouseY);
		}
	}
}
