/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.hud.gui.hud.vanilla;

import java.util.List;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.mixin.GameRendererAccessor;
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable;
import io.github.axolotlclient.modules.hud.gui.entry.AbstractHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4fStack;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class CrosshairHud extends AbstractHudEntry implements DynamicallyPositionable {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("kronhud", "crosshairhud");
	private static final Identifier CROSSHAIR_TEXTURE = Identifier.withDefaultNamespace("hud/crosshair");
	private static final Identifier ATTACK_INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier ATTACK_INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier ATTACK_INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
	private final EnumOption<Crosshair> type = new EnumOption<>("crosshair_type", Crosshair.class, Crosshair.CROSS);
	private final BooleanOption showInF5 = new BooleanOption("showInF5", false);
	private final ColorOption defaultColor = new ColorOption("defaultcolor", ClientColors.WHITE);
	private final ColorOption entityColor = new ColorOption("entitycolor", ClientColors.SELECTOR_RED);
	private final ColorOption containerColor = new ColorOption("blockcolor", ClientColors.SELECTOR_BLUE);
	private final ColorOption attackIndicatorBackgroundColor = new ColorOption("attackindicatorbg",
		new Color(0xFF141414));
	private final ColorOption attackIndicatorForegroundColor = new ColorOption("attackindicatorfg", ClientColors.WHITE);
	private final BooleanOption applyBlend = new BooleanOption("applyBlend", true);
	private final BooleanOption overrideF3 = new BooleanOption("overrideF3", false);
	private final BooleanOption customAttackIndicator = new BooleanOption("crosshairhud.custom_attack_indicator", false);

	private final GraphicsOption customTextureGraphics = new GraphicsOption("customTextureGraphics",
		new int[][]{
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
		});
	private final EnumOption<TargetCrosshair> entityCrosshairType = new EnumOption<>("entity_crosshair_type", TargetCrosshair.class, TargetCrosshair.CROSSHAIR_TYPE_DEFAULT);
	private final EnumOption<TargetCrosshair> containerCrosshairType = new EnumOption<>("container_crosshair_type", TargetCrosshair.class, TargetCrosshair.CROSSHAIR_TYPE_DEFAULT);
	private final GraphicsOption entityCustomTextureGraphics = new GraphicsOption("entity_crosshair_graphics", customTextureGraphics.getDefault());
	private final GraphicsOption containerCustomTextureGraphics = new GraphicsOption("container_crosshair_graphics", customTextureGraphics.getDefault());
	private final Minecraft client = (Minecraft) super.client;

	public CrosshairHud() {
		super(15, 15);
	}

	@Override
	public boolean movable() {
		return false;
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(type);
		options.add(customTextureGraphics);
		options.add(entityCrosshairType);
		options.add(entityCustomTextureGraphics);
		options.add(containerCrosshairType);
		options.add(containerCustomTextureGraphics);
		options.add(showInF5);
		options.add(overrideF3);
		options.add(applyBlend);
		options.add(defaultColor);
		options.add(entityColor);
		options.add(containerColor);
		options.add(customAttackIndicator);
		options.add(attackIndicatorBackgroundColor);
		options.add(attackIndicatorForegroundColor);
		return options;
	}

	@Override
	public boolean overridesF3() {
		return overrideF3.get();
	}

	@Override
	public double getDefaultX() {
		return 0.5;
	}

	@Override
	public double getDefaultY() {
		return 0.5;
	}

	private static final RenderPipeline CROSSHAIR_NO_TEX = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation("pipeline/crosshair_no_tex")
		.withColorTargetState(new ColorTargetState(BlendFunction.INVERT))
		.build()
	);

	@Override
	public void render(AxoRenderContext graphics, float delta) {
	}

	public void renderCrosshair(GuiGraphicsExtractor graphics) {
		if (!client.options.getCameraType().isFirstPerson() && !showInF5.get()) {
			return;
		}
		if (client.gui.hud.getDebugOverlay().showDebugScreen() && !overridesF3()) {
			return;
		}
		if (isHidden()) {
			return;
		}

		graphics.pose().pushMatrix();
		scale(graphics);

		int x = getPos().x();
		int y = getPos().y() + 1;
		var mode = getMode();
		Color color = (switch (mode) {
			case DEFAULT -> defaultColor;
			case ENTITY -> entityColor;
			case CONTAINER -> containerColor;
		}).get();
		Crosshair defaultType = this.type.get();
		var typeOption = (switch (mode) {
			case DEFAULT -> this.type;
			case ENTITY -> entityCrosshairType;
			case CONTAINER -> containerCrosshairType;
		}).get();
		var type = typeOption instanceof Crosshair ? typeOption : ((TargetCrosshair) typeOption).asCrosshair(defaultType);
		AttackIndicatorStatus indicator = this.client.options.attackIndicator().get();

		boolean blend = ClientColors.ARGB.opaque(color.toInt()) == ClientColors.WHITE.toInt() && !type.equals(Crosshair.DIRECTION) && applyBlend.get();

		boolean isTex = type.equals(Crosshair.TEXTURE) || type.equals(Crosshair.CUSTOM);
		if (type.equals(Crosshair.DOT)) {
			fillRenderType(graphics, blend, x + (getWidth() / 2) - 2, y + (getHeight() / 2) - 2, 3, 3, color);
		} else if (type.equals(Crosshair.CROSS)) {
			fillRenderType(graphics, blend, x + (getWidth() / 2) - 6, y + (getHeight() / 2) - 1, 6, 1, color);
			fillRenderType(graphics, blend, x + (getWidth() / 2), y + (getHeight() / 2) - 1, 5, 1, color);
			fillRenderType(graphics, blend, x + (getWidth() / 2) - 1, y + (getHeight() / 2) - 6, 1, 5, color);
			fillRenderType(graphics, blend, x + (getWidth() / 2) - 1, y + (getHeight() / 2), 1, 5, color);
		} else if (!type.equals(Crosshair.DIRECTION) && isTex) {
			if (type.equals(Crosshair.TEXTURE)) {
				// Draw crosshair
				graphics.blitSprite(renderType(blend), CROSSHAIR_TEXTURE,
					(int) (((graphics.guiWidth() / getScale()) - 15) / 2),
					(int) (((graphics.guiHeight() / getScale()) - 15) / 2), 15, 15, color.toInt());
			} else {
				// Draw crosshair
				graphics.blit(renderType(blend), Util.getTexture(switch (mode) {
						case DEFAULT -> customTextureGraphics;
						case ENTITY ->
							typeOption == TargetCrosshair.CROSSHAIR_TYPE_DEFAULT ? customTextureGraphics : entityCustomTextureGraphics;
						case CONTAINER ->
							typeOption == TargetCrosshair.CROSSHAIR_TYPE_DEFAULT ? customTextureGraphics : containerCustomTextureGraphics;
					}), (int) (((graphics.guiWidth() / getScale()) - 15) / 2),
					(int) (((graphics.guiHeight() / getScale()) - 15) / 2), 0, 0, 15, 15, 15, 15, color.toInt());
			}

			// Draw attack indicator
			if (!customAttackIndicator.get() && indicator == AttackIndicatorStatus.CROSSHAIR) {
				//noinspection DataFlowIssue
				float progress = this.client.player.getAttackStrengthScale(0.0F);

				// Whether a cross should be displayed under the indicator
				boolean targetingEntity = false;
				if (this.client.crosshairPickEntity != null && this.client.crosshairPickEntity instanceof LivingEntity
					&& progress >= 1.0F) {
					targetingEntity = this.client.player.getCurrentItemAttackStrengthDelay() > 5.0F;
					targetingEntity &= this.client.crosshairPickEntity.isAlive();

					AttackRange attackRange = this.client.player.getActiveItem().get(DataComponents.ATTACK_RANGE);
					//noinspection DataFlowIssue
					targetingEntity &= attackRange == null || attackRange.isInRange(this.client.player, this.client.hitResult.getLocation());
				}

				x = (int) ((graphics.guiWidth() / getScale()) / 2 - 8);
				y = (int) ((graphics.guiHeight() / getScale()) / 2 - 7 + 16);

				if (targetingEntity) {
					graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_FULL, x, y, 16, 16);
				} else if (progress < 1.0F) {
					int k = (int) (progress * 17.0F);
					graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4);
					graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, k, 4);
				}
			}
		}
		if ((isTex ? customAttackIndicator.get() : true) && indicator == AttackIndicatorStatus.CROSSHAIR) {
			//noinspection DataFlowIssue
			float progress = this.client.player.getAttackStrengthScale(0.0F);
			if (progress != 1.0F) {
				var blendIndicator = applyBlend.get() && ClientColors.ARGB.opaque(attackIndicatorForegroundColor.get().toInt()) == ClientColors.WHITE.toInt();
				fillRenderType(graphics, blendIndicator, getRawX() + (getWidth() / 2) - 6, getRawY() + (getHeight() / 2) + 9,
					11, 1, attackIndicatorBackgroundColor.get());
				fillRenderType(graphics, blendIndicator,
					getRawX() + (getWidth() / 2) - 6, getRawY() + (getHeight() / 2) + 9,
					(int) (progress * 11), 1, attackIndicatorForegroundColor.get());
			}
		}
		graphics.pose().popMatrix();
	}

	public void renderDirectionCrosshair() {
		if (type.get() != Crosshair.DIRECTION) {
			return;
		}
		if (!client.options.getCameraType().isFirstPerson() && !showInF5.get()) {
			return;
		}
		if (client.gui.hud.getDebugOverlay().showDebugScreen() && !overridesF3()) {
			return;
		}

		Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
		matrixStack.pushMatrix();
		var scaleModifier = getScale() < 1 ? 0 : (int) ((getScale() - 1f) * 15f);
		((GameRendererAccessor) client.gameRenderer).getDebugCrosshairRenderer().render(client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState,
			client.gameRenderer.gameRenderState().windowRenderState.guiScale + scaleModifier);
		matrixStack.popMatrix();
	}

	private RenderPipeline renderType(boolean blend) {
		return blend ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI_TEXTURED;
	}

	private void fillRenderType(GuiGraphicsExtractor graphics, boolean blend, int x, int y, int width, int height, Color color) {
		if (blend) {
			graphics.fill(CROSSHAIR_NO_TEX, x, y, width + x, height + y, color.toInt());
		} else {
			graphics.fill(x, y, width + x, height + y, color.toInt());
		}
	}

	private CrosshairMode getMode() {
		HitResult hit = client.hitResult;
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return CrosshairMode.DEFAULT;
		} else if (hit.getType() == HitResult.Type.ENTITY) {
			return CrosshairMode.ENTITY;
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos blockPos = ((BlockHitResult) hit).getBlockPos();
			Level world = this.client.level;
			//noinspection DataFlowIssue
			if (world.getBlockState(blockPos).getMenuProvider(world, blockPos) != null
				|| world.getBlockState(blockPos).getBlock() instanceof AbstractChestBlock<?>) {
				return CrosshairMode.CONTAINER;
			}
		}
		return CrosshairMode.DEFAULT;
	}

	@Override
	public void renderPlaceholder(AxoRenderContext graphics, float delta) {
		// Shouldn't need this...
	}

	@Override
	public AnchorPoint getAnchor() {
		return AnchorPoint.MIDDLE_MIDDLE;
	}

	private enum CrosshairMode {
		DEFAULT, ENTITY, CONTAINER
	}

	private enum Crosshair {
		CROSS, DOT, DIRECTION, TEXTURE, CUSTOM
	}

	private enum TargetCrosshair {
		CROSS, DOT, DIRECTION, TEXTURE, CUSTOM, CROSSHAIR_TYPE_DEFAULT;

		public Crosshair asCrosshair(Crosshair defaultType) {
			return switch (this) {
				case CROSS -> Crosshair.CROSS;
				case DOT -> Crosshair.DOT;
				case DIRECTION -> Crosshair.DIRECTION;
				case TEXTURE -> Crosshair.TEXTURE;
				case CUSTOM -> Crosshair.CUSTOM;
				case CROSSHAIR_TYPE_DEFAULT -> defaultType;
			};
		}
	}
}
