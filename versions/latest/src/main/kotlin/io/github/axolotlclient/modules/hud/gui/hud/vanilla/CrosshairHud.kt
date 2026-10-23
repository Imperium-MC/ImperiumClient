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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.mixin.GameRendererAccessor
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.AbstractHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.Util
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.component.AttackRange
import net.minecraft.world.level.block.AbstractChestBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class CrosshairHud : AbstractHudEntry(15, 15), DynamicallyPositionable {
    private val type = EnumOption("crosshair_type", Crosshair::class.java, Crosshair.CROSS)
    private val showInF5 = BooleanOption("showInF5", false)
    private val defaultColor = ColorOption("defaultcolor", ClientColors.WHITE)
    private val entityColor = ColorOption("entitycolor", ClientColors.SELECTOR_RED)
    private val containerColor = ColorOption("blockcolor", ClientColors.SELECTOR_BLUE)
    private val attackIndicatorBackgroundColor = ColorOption("attackindicatorbg", Color(0xFF141414.toInt()))
    private val attackIndicatorForegroundColor = ColorOption("attackindicatorfg", ClientColors.WHITE)
    private val applyBlend = BooleanOption("applyBlend", true)
    private val overrideF3 = BooleanOption("overrideF3", false)
    private val customAttackIndicator = BooleanOption("crosshairhud.custom_attack_indicator", false)

    // Alpine profile settings (M6): dynamic_color gates the target-based recolouring
    // (off = always the default colour); friendly_color replaces the entity colour while
    // the crosshair target is an ally (vanilla scoreboard team / isAlliedTo).
    private val dynamicColor = BooleanOption("crosshairhud.dynamic_color", true)
    private val friendlyColor = ColorOption("crosshairhud.friendly_color", ClientColors.SELECTOR_GREEN)

    private val customTextureGraphics = GraphicsOption(
        "customTextureGraphics",
        arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        )
    )
    private val entityCrosshairType = EnumOption("entity_crosshair_type", TargetCrosshair::class.java, TargetCrosshair.CROSSHAIR_TYPE_DEFAULT)
    private val containerCrosshairType = EnumOption("container_crosshair_type", TargetCrosshair::class.java, TargetCrosshair.CROSSHAIR_TYPE_DEFAULT)
    private val entityCustomTextureGraphics = GraphicsOption("entity_crosshair_graphics", customTextureGraphics.getDefault())
    private val containerCustomTextureGraphics = GraphicsOption("container_crosshair_graphics", customTextureGraphics.getDefault())
    private val mc: Minecraft = super.client as Minecraft

    override fun movable(): Boolean = false

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(hide)
        options.add(type)
        options.add(customTextureGraphics)
        options.add(entityCrosshairType)
        options.add(entityCustomTextureGraphics)
        options.add(containerCrosshairType)
        options.add(containerCustomTextureGraphics)
        options.add(showInF5)
        options.add(overrideF3)
        options.add(applyBlend)
        options.add(defaultColor)
        options.add(entityColor)
        options.add(friendlyColor)
        options.add(dynamicColor)
        options.add(containerColor)
        options.add(customAttackIndicator)
        options.add(attackIndicatorBackgroundColor)
        options.add(attackIndicatorForegroundColor)
        return options
    }

    override fun overridesF3(): Boolean = overrideF3.get()

    override fun getDefaultX(): Double = 0.5

    override fun getDefaultY(): Double = 0.5

    override fun render(graphics: AxoRenderContext, delta: Float) {
    }

    fun renderCrosshair(graphics: GuiGraphicsExtractor) {
        if (!mc.options.getCameraType().isFirstPerson && !showInF5.get()) {
            return
        }
        if (mc.gui.hud.getDebugOverlay().showDebugScreen() && !overridesF3()) {
            return
        }
        if (isHidden()) {
            return
        }

        graphics.pose().pushMatrix()
        scale(graphics)

        var x = getPos().x()
        var y = getPos().y() + 1
        val mode = getMode()
        val color = getModeColor(mode)
        val defaultType = this.type.get()
        val typeOption = when (mode) {
            CrosshairMode.DEFAULT -> this.type.get()
            CrosshairMode.ENTITY -> entityCrosshairType.get()
            CrosshairMode.CONTAINER -> containerCrosshairType.get()
        }
        val crosshairType = if (typeOption is Crosshair) typeOption else (typeOption as TargetCrosshair).asCrosshair(defaultType)
        val indicator = this.mc.options.attackIndicator().get()

        val blend = ClientColors.ARGB.opaque(color.toInt()) == ClientColors.WHITE.toInt() &&
            crosshairType != Crosshair.DIRECTION && applyBlend.get()

        val isTex = crosshairType == Crosshair.TEXTURE || crosshairType == Crosshair.CUSTOM
        if (crosshairType == Crosshair.DOT) {
            fillRenderType(graphics, blend, x + (getWidth() / 2) - 2, y + (getHeight() / 2) - 2, 3, 3, color)
        } else if (crosshairType == Crosshair.CROSS) {
            fillRenderType(graphics, blend, x + (getWidth() / 2) - 6, y + (getHeight() / 2) - 1, 6, 1, color)
            fillRenderType(graphics, blend, x + (getWidth() / 2), y + (getHeight() / 2) - 1, 5, 1, color)
            fillRenderType(graphics, blend, x + (getWidth() / 2) - 1, y + (getHeight() / 2) - 6, 1, 5, color)
            fillRenderType(graphics, blend, x + (getWidth() / 2) - 1, y + (getHeight() / 2), 1, 5, color)
        } else if (crosshairType != Crosshair.DIRECTION && isTex) {
            if (crosshairType == Crosshair.TEXTURE) {
                // Draw crosshair
                graphics.blitSprite(
                    renderType(blend), CROSSHAIR_TEXTURE,
                    ((graphics.guiWidth() / getScale() - 15) / 2).toInt(),
                    ((graphics.guiHeight() / getScale() - 15) / 2).toInt(), 15, 15, color.toInt()
                )
            } else {
                // Draw crosshair
                graphics.blit(
                    renderType(blend), Util.getTexture(
                        when (mode) {
                            CrosshairMode.DEFAULT -> customTextureGraphics
                            CrosshairMode.ENTITY ->
                                if (typeOption == TargetCrosshair.CROSSHAIR_TYPE_DEFAULT) customTextureGraphics else entityCustomTextureGraphics
                            CrosshairMode.CONTAINER ->
                                if (typeOption == TargetCrosshair.CROSSHAIR_TYPE_DEFAULT) customTextureGraphics else containerCustomTextureGraphics
                        }
                    ), ((graphics.guiWidth() / getScale() - 15) / 2).toInt(),
                    ((graphics.guiHeight() / getScale() - 15) / 2).toInt(), 0f, 0f, 15, 15, 15, 15, color.toInt()
                )
            }

            // Draw attack indicator
            if (!customAttackIndicator.get() && indicator == AttackIndicatorStatus.CROSSHAIR) {
                val progress = this.mc.player!!.getAttackStrengthScale(0.0f)

                // Whether a cross should be displayed under the indicator
                var targetingEntity = false
                if (this.mc.crosshairPickEntity != null && this.mc.crosshairPickEntity is LivingEntity &&
                    progress >= 1.0f
                ) {
                    targetingEntity = this.mc.player!!.getCurrentItemAttackStrengthDelay() > 5.0f
                    targetingEntity = targetingEntity and (this.mc.crosshairPickEntity!!.isAlive)

                    val attackRange: AttackRange? =
                        this.mc.player!!.activeItem.get(DataComponents.ATTACK_RANGE)
                    targetingEntity = targetingEntity and (attackRange == null ||
                        attackRange.isInRange(this.mc.player!!, this.mc.hitResult!!.location))
                }

                x = ((graphics.guiWidth() / getScale()) / 2 - 8).toInt()
                y = ((graphics.guiHeight() / getScale()) / 2 - 7 + 16).toInt()

                if (targetingEntity) {
                    graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_FULL, x, y, 16, 16)
                } else if (progress < 1.0f) {
                    val k = (progress * 17.0f).toInt()
                    graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4)
                    graphics.blitSprite(renderType(applyBlend.get()), ATTACK_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, k, 4)
                }
            }
        }
        if ((if (isTex) customAttackIndicator.get() else true) && indicator == AttackIndicatorStatus.CROSSHAIR) {
            val progress = this.mc.player!!.getAttackStrengthScale(0.0f)
            if (progress != 1.0f) {
                val blendIndicator = applyBlend.get() &&
                    ClientColors.ARGB.opaque(attackIndicatorForegroundColor.get().toInt()) == ClientColors.WHITE.toInt()
                fillRenderType(
                    graphics, blendIndicator, getRawX() + (getWidth() / 2) - 6, getRawY() + (getHeight() / 2) + 9,
                    11, 1, attackIndicatorBackgroundColor.get()
                )
                fillRenderType(
                    graphics, blendIndicator,
                    getRawX() + (getWidth() / 2) - 6, getRawY() + (getHeight() / 2) + 9,
                    (progress * 11).toInt(), 1, attackIndicatorForegroundColor.get()
                )
            }
        }
        graphics.pose().popMatrix()
    }

    /**
     * Colour for the current aim mode: the default colour always, unless dynamic
     * recolouring is on — then the container colour over containers and the entity
     * colour over entities, with the friendly colour when that entity is an ally.
     */
    private fun getModeColor(mode: CrosshairMode): Color {
        if (!dynamicColor.get()) {
            return defaultColor.get()
        }
        if (mode == CrosshairMode.CONTAINER) {
            return containerColor.get()
        }
        if (mode == CrosshairMode.ENTITY && mc.player != null && mc.crosshairPickEntity != null &&
            mc.player!!.isAlliedTo(mc.crosshairPickEntity)
        ) {
            return friendlyColor.get()
        }
        return when (mode) {
            CrosshairMode.DEFAULT -> defaultColor
            CrosshairMode.ENTITY -> entityColor
            CrosshairMode.CONTAINER -> containerColor
        }.get()
    }

    fun renderDirectionCrosshair() {
        if (type.get() != Crosshair.DIRECTION) {
            return
        }
        if (!mc.options.getCameraType().isFirstPerson && !showInF5.get()) {
            return
        }
        if (mc.gui.hud.getDebugOverlay().showDebugScreen() && !overridesF3()) {
            return
        }

        val matrixStack = RenderSystem.getModelViewStack()
        matrixStack.pushMatrix()
        val scaleModifier = if (getScale() < 1) 0 else ((getScale() - 1f) * 15f).toInt()
        val gameRenderer = mc.gameRenderer
        val accessor = gameRenderer as GameRendererAccessor
        accessor.getDebugCrosshairRenderer().render(
            gameRenderer.gameRenderState().levelRenderState.cameraRenderState,
            gameRenderer.gameRenderState().windowRenderState.guiScale + scaleModifier,
            gameRenderer.mainRenderTarget().getColorTextureView()!!,
            if (accessor.getAppliedPostEffects().isNotEmpty()) {
                accessor.getHud3dTarget().getDepthTextureView()!!
            } else {
                gameRenderer.mainRenderTarget().getDepthTextureView()!!
            }
        )
        matrixStack.popMatrix()
    }

    private fun renderType(blend: Boolean): RenderPipeline =
        if (blend) RenderPipelines.CROSSHAIR else RenderPipelines.GUI_TEXTURED

    private fun fillRenderType(
        graphics: GuiGraphicsExtractor, blend: Boolean, x: Int, y: Int, width: Int, height: Int, color: Color
    ) {
        if (blend) {
            graphics.fill(CROSSHAIR_NO_TEX, x, y, width + x, height + y, color.toInt())
        } else {
            graphics.fill(x, y, width + x, height + y, color.toInt())
        }
    }

    private fun getMode(): CrosshairMode {
        val hit = mc.hitResult
        if (hit == null || hit.type == HitResult.Type.MISS) {
            return CrosshairMode.DEFAULT
        } else if (hit.type == HitResult.Type.ENTITY) {
            return CrosshairMode.ENTITY
        } else if (hit.type == HitResult.Type.BLOCK) {
            val blockPos = (hit as BlockHitResult).blockPos
            val world = this.mc.level
            if (world != null) {
                if (world.getBlockState(blockPos).getMenuProvider(world, blockPos) != null ||
                    world.getBlockState(blockPos).block is AbstractChestBlock<*>
                ) {
                    return CrosshairMode.CONTAINER
                }
            }
        }
        return CrosshairMode.DEFAULT
    }

    override fun renderPlaceholder(graphics: AxoRenderContext, delta: Float) {
        // Shouldn't need this...
    }

    override fun getAnchor(): AnchorPoint = AnchorPoint.MIDDLE_MIDDLE

    private enum class CrosshairMode {
        DEFAULT, ENTITY, CONTAINER
    }

    private enum class Crosshair {
        CROSS, DOT, DIRECTION, TEXTURE, CUSTOM
    }

    private enum class TargetCrosshair {
        CROSS, DOT, DIRECTION, TEXTURE, CUSTOM, CROSSHAIR_TYPE_DEFAULT;

        fun asCrosshair(defaultType: Crosshair): Crosshair = when (this) {
            CROSS -> Crosshair.CROSS
            DOT -> Crosshair.DOT
            DIRECTION -> Crosshair.DIRECTION
            TEXTURE -> Crosshair.TEXTURE
            CUSTOM -> Crosshair.CUSTOM
            CROSSHAIR_TYPE_DEFAULT -> defaultType
        }
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("kronhud", "crosshairhud")
        private val CROSSHAIR_TEXTURE = Identifier.withDefaultNamespace("hud/crosshair")
        private val ATTACK_INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full")
        private val ATTACK_INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background")
        private val ATTACK_INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress")

        private val CROSSHAIR_NO_TEX: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation("pipeline/crosshair_no_tex")
                .withColorTargetState(ColorTargetState(BlendFunction.INVERT))
                .build()
        )
    }
}
