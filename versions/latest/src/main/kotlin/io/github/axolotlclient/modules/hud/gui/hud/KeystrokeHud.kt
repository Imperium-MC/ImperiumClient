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

package io.github.axolotlclient.modules.hud.gui.hud

import java.nio.file.Files
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.Optional
import java.util.stream.Collectors

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.config.profiles.ProfileAware
import io.github.axolotlclient.modules.hud.ClickInputTracker
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.gui.keystrokes.KeystrokePositioningScreen
import io.github.axolotlclient.modules.hud.gui.keystrokes.KeystrokesScreen
import io.github.axolotlclient.modules.hud.gui.layout.Justification
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.modules.hud.util.Rectangle
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.events.Events
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util as McUtil
import kotlin.math.max
import kotlin.math.min

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class KeystrokeHud : TextHudEntry(53, 61, true), ProfileAware {

    private val pressedTextColor = ColorOption("heldtextcolor", Color(0xFF000000.toInt()))
    private val pressedBackgroundColor = ColorOption("heldbackgroundcolor", Color(0x64FFFFFF.toInt()))
    private val pressedOutlineColor = ColorOption("heldoutlinecolor", ClientColors.BLACK)

    private val keystrokesOption = GenericOption("keystrokes", "keystrokes.configure") {
        mc.gui.setScreen(KeystrokesScreen(this, mc.gui.screen()))
    }
    private val configurePositions = GenericOption("keystrokes.positions", "keystrokes.positions.configure") {
        mc.gui.setScreen(KeystrokePositioningScreen(mc.gui.screen(), this))
    }
    private val animationTime = IntegerOption("keystrokes.animation_time", 100, 0, 500)

    // Alpine parity (keystrokes): which default keys are laid out, whether the mouse-key
    // strokes carry their CPS readout, the space bar's style, and the default grid size.
    // Each toggle rebuilds the layout (keystrokes == null -> re-derived next tick) so the
    // change is visible immediately, saved customizations excepted.
    private val displaySpace = BooleanOption("keystrokes.display_space", true) { keystrokes = null }
    private val fullSpace = BooleanOption("keystrokes.full_space", false)
    private val displayMouseButtons = BooleanOption("keystrokes.display_mouse_buttons", true) { keystrokes = null }
    private val displayCps = BooleanOption("keystrokes.display_cps", true)
    private val keySize = IntegerOption("keystrokes.key_size", 17, 10, 30)

    @JvmField
    var keystrokes: ArrayList<Keystroke>? = null

    private val mc: Minecraft = super.client as Minecraft

    init {
        Events.KEYBIND_CHANGE.register { _ ->
            if (Minecraft.getInstance().getWindow() != null) {
                KeyMapping.releaseAll()
                KeyMapping.setAll()
            }
        }
    }

    fun setDefaultKeystrokes() {
        val pos: DrawPosition = contentPos
        val u = keySize.get()
        val rowGap = u + 1
        val gridWidth = 3 * rowGap - 1
        val mouseW = (gridWidth - 1) / 2
        val mouseH = max(10, u)
        if (displayMouseButtons.get()) {
            // LMB
            keystrokes!!.add(createFromKey(Rectangle(0, rowGap + u, mouseW, mouseH), pos, mc.options.keyAttack))
            // RMB
            keystrokes!!.add(createFromKey(Rectangle(mouseW + 1, rowGap + u, mouseW, mouseH), pos, mc.options.keyUse))
        }
        // W
        keystrokes!!.add(createFromKey(Rectangle(rowGap, 0, u, u), pos, mc.options.keyUp))
        // A
        keystrokes!!.add(createFromKey(Rectangle(0, u + 1, u, u), pos, mc.options.keyLeft))
        // S
        keystrokes!!.add(createFromKey(Rectangle(rowGap, u + 1, u, u), pos, mc.options.keyDown))
        // D
        keystrokes!!.add(createFromKey(Rectangle(2 * rowGap, u + 1, u, u), pos, mc.options.keyRight))

        // Space (styles: the thin vanilla-like bar, or a full filled bar)
        if (displaySpace.get()) {
            keystrokes!!.add(
                SpecialRenderKeystroke(
                    SpecialKeystroke.SPACE, Rectangle(0, rowGap + u + mouseH + 1, gridWidth, 7), pos,
                    SpecialKeystroke.SPACE.key
                )
            )
        }
    }

    fun setKeystrokes() {
        if (mc.getWindow() == null) {
            keystrokes = null
            return
            // Wait until render is called
        }
        keystrokes = ArrayList()
        setDefaultKeystrokes()
        loadKeystrokes()
        // display_space / display_mouse_buttons also govern what a saved layout may contain:
        // strokes for the switched-off groups are dropped so the toggle always takes effect.
        keystrokes!!.removeIf { stroke: Keystroke -> isFilteredOutByOptions(stroke) }
        KeyMapping.releaseAll()
        KeyMapping.setAll()
    }

    private fun isFilteredOutByOptions(stroke: Keystroke): Boolean {
        val key = stroke.key
        if (!displaySpace.get() && key != null && key === mc.options.keyJump) {
            return true
        }
        return !displayMouseButtons.get() && key != null &&
            (key === mc.options.keyAttack || key === mc.options.keyUse)
    }

    fun createFromKey(bounds: Rectangle, offset: DrawPosition, key: KeyMapping): Keystroke {
        var name = getMouseKeyBindName(key).orElse(key.translatedKeyMessage.string.uppercase(Locale.ROOT))
        if (name.length > 4) {
            name = name.substring(0, 2)
        }
        return createFromString(bounds, offset, key, name)
    }

    fun createFromString(bounds: Rectangle, offset: DrawPosition, key: KeyMapping, word: String): Keystroke =
        LabelKeystroke(bounds, offset, key, word, true, Justification.CENTER)

    override fun render(graphics: io.github.axolotlclient.bridge.render.AxoRenderContext, delta: Float) {
        graphics.`br$pushMatrix`()
        scale(graphics)
        renderComponent(graphics, delta)
        graphics.`br$popMatrix`()
    }

    override fun renderComponent(graphics: io.github.axolotlclient.bridge.render.AxoRenderContext, delta: Float) {
        if (keystrokes == null) {
            setKeystrokes()
        }
        for (stroke in keystrokes!!) {
            stroke.render(graphics as GuiGraphicsExtractor)
        }
    }

    override fun renderPlaceholderComponent(graphics: io.github.axolotlclient.bridge.render.AxoRenderContext, delta: Float) {
        renderComponent(graphics, delta)
    }

    override fun tickable(): Boolean = true

    override fun tick() {
        val pos: DrawPosition = contentPos
        if (keystrokes == null) {
            setKeystrokes()
        }
        for (stroke in keystrokes!!) {
            stroke.offset = pos
        }
    }

    override fun getShadowDefault(): Boolean = false

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        // We want a specific order since this is a more complicated entry
        val options = ArrayList<Option<*>>()
        options.add(enabled)
        options.add(scale)
        options.add(anchor)
        options.add(textColor)
        options.add(pressedTextColor)
        options.add(shadow)
        options.add(background!!)
        options.add(backgroundColor!!)
        options.add(pressedBackgroundColor)
        options.add(outline!!)
        options.add(outlineColor!!)
        options.add(pressedOutlineColor)
        options.add(roundBackground!!)
        options.add(backgroundRounding!!)
        options.add(animationTime)
        options.add(keySize)
        options.add(displaySpace)
        options.add(fullSpace)
        options.add(displayMouseButtons)
        options.add(displayCps)
        options.add(keystrokesOption)
        options.add(configurePositions)
        return options
    }

    override fun getId(): Identifier = ID

    override fun reloadConfig() {
        keystrokes = null
    }

    override fun saveConfig() {
        saveKeystrokes()
    }

    fun interface KeystrokeRenderer {
        fun render(stroke: Keystroke, graphics: GuiGraphicsExtractor)
    }

    abstract inner class Keystroke internal constructor(
        val bounds: Rectangle, var offset: DrawPosition, key: KeyMapping?, render: KeystrokeRenderer
    ) {
        var key: KeyMapping? = key
            private set
        var render: KeystrokeRenderer = render
            private set

        private var start: Long = -1
        private var wasPressed = false

        open fun setKey(key: KeyMapping?) {
            this.key = key
        }

        fun setX(x: Int) {
            bounds.x(x - offset.x())
        }

        fun setY(y: Int) {
            bounds.y(y - offset.y())
        }

        fun getRenderPosition(): Rectangle = bounds.offset(offset)

        val fgColor: Color
            get() = if (isKeyDown()) ClientColors.blend(textColor.get(), pressedTextColor.get(), getPercentPressed())
            else ClientColors.blend(pressedTextColor.get(), textColor.get(), getPercentPressed())

        private fun getPercentPressed(): Float =
            if (start == -1L) 1f else Mth.clamp((McUtil.getMillis() - start).toFloat() / getAnimTime(), 0f, 1f)

        fun render(matrices: GuiGraphicsExtractor) {
            renderStroke(matrices)
            render.render(this, matrices)
        }

        fun renderStroke(matrices: GuiGraphicsExtractor) {
            if (isKeyDown() != wasPressed) {
                start = McUtil.getMillis()
            }
            val rect = getRenderPosition()
            if (background!!.get()) {
                if (roundBackground!!.get()) {
                    matrices.`br$fillRectRound`(
                        rect, getColor(), min(min(rect.height(), rect.width()) / 2f, backgroundRounding!!.get().toFloat())
                    )
                } else {
                    matrices.`br$fillRect`(rect, getColor())
                }
            }
            if (outline!!.get()) {
                if (roundBackground!!.get()) {
                    matrices.`br$outlineRectRound`(
                        rect, getOutlineColor(), min(min(rect.height(), rect.width()) / 2f, backgroundRounding!!.get().toFloat())
                    )
                } else {
                    matrices.`br$outlineRect`(rect, getOutlineColor())
                }
            }
            if ((McUtil.getMillis() - start).toFloat() / getAnimTime() >= 1) {
                start = -1
            }
            wasPressed = isKeyDown()
        }

        private fun getAnimTime(): Int = animationTime.get()

        private fun isKeyDown(): Boolean = key != null && key!!.isDown

        fun getColor(): Color =
            if (isKeyDown()) ClientColors.blend(backgroundColor!!.get(), pressedBackgroundColor.get(), getPercentPressed())
            else ClientColors.blend(pressedBackgroundColor.get(), backgroundColor!!.get(), getPercentPressed())

        fun getOutlineColor(): Color =
            if (isKeyDown()) ClientColors.blend(outlineColor!!.get(), pressedOutlineColor.get(), getPercentPressed())
            else ClientColors.blend(pressedOutlineColor.get(), outlineColor!!.get(), getPercentPressed())

        open fun serialize(): MutableMap<String, Any> {
            val map = HashMap<String, Any>()
            map["key"] = key!!.saveString()
            map["key_name"] = key!!.getName()
            map["bounds"] = mapOf("x" to bounds.x(), "y" to bounds.y(), "width" to bounds.width(), "height" to bounds.height())
            return map
        }

        open fun getLabel(): String = NO_LABEL.get()

        open fun setLabel(label: String?) {
        }

        open fun isLabelEditable(): Boolean = false
    }

    private fun deserializeKey(json: Map<String, Any>): Keystroke {
        val type = json["type"] as String?
        return when (type) {
            "custom_render" -> {
                val key = KeyMapping.get(json["key_name"] as String)
                CustomRenderKeystroke(getRectangle(json["bounds"] as Map<String, *>), contentPos, key, json["graphics"] as String?)
            }

            "option" -> {
                val key = KeyMapping.get(json.getOrDefault("key_name", json["option"]) as String? ?: "")
                SpecialRenderKeystroke(
                    SpecialKeystroke.byId[(json["special_name"] as String).lowercase(Locale.ROOT)]!!,
                    getRectangle(json["bounds"] as Map<String, *>), contentPos, key
                )
            }

            else -> {
                val key = KeyMapping.get(json["key_name"] as String? ?: "")
                LabelKeystroke(
                    getRectangle(json["bounds"] as Map<String, *>), contentPos, key, json["label"] as String?,
                    json["synchronize_label"] as Boolean,
                    Justification.valueOf(json.getOrDefault("justification", "CENTER") as String)
                )
            }
        }
    }

    fun newSpecialStroke(stroke: SpecialKeystroke): Keystroke = SpecialRenderKeystroke(stroke)

    fun newStroke(): LabelKeystroke {
        val u = keySize.get()
        return LabelKeystroke(Rectangle(0, 0, u, u), contentPos, null, "", false, Justification.CENTER)
    }

    fun newCustomStroke(): Keystroke {
        val u = keySize.get()
        return CustomRenderKeystroke(Rectangle(0, 0, u, u), contentPos, null, null)
    }

    inner class CustomRenderKeystroke(bounds: Rectangle, offset: DrawPosition, key: KeyMapping?, graphic: String?) :
        Keystroke(bounds, offset, key, KeystrokeRenderer { stroke, graphics ->
            val b = stroke.bounds
            val xC = b.x() + stroke.offset.x() + b.width() / 2f
            val yC = b.y() + stroke.offset.y() + b.height() / 2f
            val g = (stroke as CustomRenderKeystroke).graphics
            val gW = g.get().getWidth()
            val gH = g.get().getHeight()
            val color = stroke.fgColor.toInt()
            val texture = io.github.axolotlclient.util.Util.getTexture(g)
            graphics.`br$pushMatrix`()
            graphics.`br$translateMatrix`(xC - gW / 2f, yC - gH / 2f)
            if (shadow.get()) {
                graphics.`br$translateMatrix`(1f, 1f)
                graphics.`axolotlclient_rendering$roundedBlit`(
                    texture, 0, 0, gW, gH, 0f, 1f, 0f, 1f, ClientColors.ARGB.scaleRGB(color, 0.25f), 0f
                )
                graphics.`br$translateMatrix`(-1f, -1f)
            }
            graphics.`axolotlclient_rendering$roundedBlit`(texture, 0, 0, gW, gH, 0f, 1f, 0f, 1f, color, 0f)
            graphics.`br$popMatrix`()
        }) {

        @JvmField
        val graphics = GraphicsOption("custom_render_stroke" + hashCode(), DEFAULT_SIZE, DEFAULT_SIZE)

        init {
            if (graphic != null) {
                graphics.fromSerializedValue(graphic)
            }
        }

        fun getSize(): Int = graphics.get().getWidth()

        fun setSize(size: Int) {
            if (size != getSize()) {
                val newGraphics = io.github.axolotlclient.AxolotlClientConfig.impl.util.GraphicsImpl(size, size)
                graphics.get().copyTo(newGraphics)
                graphics.set(newGraphics)
            }
        }

        override fun serialize(): MutableMap<String, Any> {
            val json = super.serialize()
            json["type"] = "custom_render"
            json["graphics"] = graphics.toSerializedValue()
            return json
        }

    }

    inner class SpecialRenderKeystroke(stroke: SpecialKeystroke, bounds: Rectangle, offset: DrawPosition, key: KeyMapping?) :
        Keystroke(
            bounds, offset, key,
            KeystrokeRenderer { s, g -> stroke.renderer.render(this@KeystrokeHud, s, g) }
        ) {

        private val parent: SpecialKeystroke = stroke

        constructor(stroke: SpecialKeystroke) : this(stroke, stroke.rect.copy(), contentPos, stroke.key)

        override fun serialize(): MutableMap<String, Any> {
            val json = super.serialize()
            json["type"] = "option"
            json["special_name"] = parent.id
            return json
        }
    }

    inner class LabelKeystroke(
        bounds: Rectangle, offset: DrawPosition, key: KeyMapping?, label: String?,
        synchronizeLabel: Boolean, var justification: Justification
    ) : Keystroke(
        bounds, offset, key,
        KeystrokeRenderer { stroke, matrices ->
            val strokeBounds = stroke.bounds
            val labelKeystroke = stroke as LabelKeystroke
            val x = strokeBounds.x() + stroke.offset.x() + 2 +
                labelKeystroke.justification.getXOffset(stroke.getLabel(), strokeBounds.width() - 3)
            val y = strokeBounds.y() + stroke.offset.y() + (strokeBounds.height() / 2f) - 4

            io.github.axolotlclient.util.DrawUtil.drawString(
                matrices, stroke.getLabel(), x, y.toInt(), stroke.fgColor.toInt(), shadow.get()
            )
        }
    ) {

        private var label: String? = label
        var isSynchronizeLabel: Boolean = false
            private set

        init {
            setSynchronizeLabel(synchronizeLabel)
        }

        override fun setKey(key: KeyMapping?) {
            if (isSynchronizeLabel && key != null) {
                var name = getMouseKeyBindName(key).orElse(key.translatedKeyMessage.string.uppercase(Locale.ROOT))
                if (name.length > 4) {
                    name = name.substring(0, 2)
                }
                this.label = name
            }
            super.setKey(key)
        }

        override fun serialize(): MutableMap<String, Any> {
            val json = super.serialize()
            json["type"] = "custom"
            json["label"] = label!!
            json["synchronize_label"] = isSynchronizeLabel
            json["justification"] = justification.name
            return json
        }

        fun setSynchronizeLabel(synchronizeLabel: Boolean) {
            if (synchronizeLabel) {
                var name = getMouseKeyBindName(key!!)
                    .orElse(key!!.translatedKeyMessage.string.uppercase(Locale.ROOT))
                if (name.length > 4) {
                    name = name.substring(0, 2)
                }
                this.label = name
            }
            this.isSynchronizeLabel = synchronizeLabel
        }

        override fun getLabel(): String = label!!

        override fun setLabel(label: String?) {
            this.label = label
        }

        override fun isLabelEditable(): Boolean = true
    }

    fun saveKeystrokes() {
        if (keystrokes == null) return
        try {
            val path = AxolotlClientCommon.resolveProfileConfigFile(KEYSTROKE_SAVE_FILE_NAME)
            Files.createDirectories(path.parent)
            Files.writeString(
                path, GsonHelper.GSON.toJson(keystrokes!!.stream().map { obj: Keystroke -> obj.serialize() }.toList())
            )
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to save keystroke configuration!", e)
        }
    }

    fun loadKeystrokes() {
        try {
            val path = AxolotlClientCommon.resolveProfileConfigFile(KEYSTROKE_SAVE_FILE_NAME)
            if (Files.exists(path)) {
                val entries = GsonHelper.read(Files.readString(path)) as List<*>
                val loaded = entries.stream().map { e -> deserializeKey(e as Map<String, Any>) }.toList()
                if (keystrokes == null) {
                    keystrokes = ArrayList()
                } else {
                    keystrokes!!.clear()
                }
                keystrokes!!.addAll(loaded)
            } else {
                saveKeystrokes()
            }
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to load keystroke configuration, using defaults!", e)
        }
    }

    enum class SpecialKeystroke(
        val id: String, val rect: Rectangle, val key: KeyMapping, val renderer: SpecialKeystrokeRenderer
    ) {
        SPACE(
            "space", Rectangle(0, 54, 53, 7), Minecraft.getInstance().options.keyJump,
            SpecialKeystrokeRenderer { hud, stroke, matrices ->
            val bounds = stroke.bounds
            // full_space draws a filled bar across the stroke; the default keeps the
            // thin vanilla-like 1px line.
            if (hud.fullSpace.get()) {
                val fullBounds = Rectangle(
                    bounds.x() + stroke.offset.x() + 2,
                    bounds.y() + stroke.offset.y() + bounds.height() / 2 - 2, bounds.width() - 4, 4
                )
                io.github.axolotlclient.util.DrawUtil.fillRect(matrices, fullBounds, stroke.fgColor)
                if (hud.shadow.get()) {
                    io.github.axolotlclient.util.DrawUtil.fillRect(
                        matrices, fullBounds.offset(1, 1),
                        Color((stroke.fgColor.toInt() and 16579836) shr 2 or (stroke.fgColor.toInt() and -16777216))
                    )
                }
                return@SpecialKeystrokeRenderer
            }
            val spaceBounds = Rectangle(
                bounds.x() + stroke.offset.x() + 4,
                bounds.y() + stroke.offset.y() + bounds.height() / 2 - 1, bounds.width() - 8, 1
            )
            io.github.axolotlclient.util.DrawUtil.fillRect(matrices, spaceBounds, stroke.fgColor)
            if (hud.shadow.get()) {
                io.github.axolotlclient.util.DrawUtil.fillRect(
                    matrices, spaceBounds.offset(1, 1),
                    Color((stroke.fgColor.toInt() and 16579836) shr 2 or (stroke.fgColor.toInt() and -16777216))
                )
            }
        }),
        LMB_CPS(
            "lmb_cps", Rectangle(0, 36, 26, 17), Minecraft.getInstance().options.keyAttack,
            SpecialKeystrokeRenderer { hud, stroke, graphics ->
            val bounds = stroke.bounds
            val centerX = bounds.x() + stroke.offset.x() + bounds.width() / 2
            val y = bounds.y() + stroke.offset.y() + 3
            val nameY = y + bounds.height() / 4 - hud.mc.font.lineHeight / 2
            io.github.axolotlclient.util.DrawUtil.drawCenteredString(
                graphics, hud.mc.font, "LMB", centerX, nameY, stroke.fgColor, hud.shadow.get()
            )
            if (!hud.displayCps.get()) {
                return@SpecialKeystrokeRenderer
            }
            val cpsY = y + bounds.height() * 3 / 4 - hud.mc.font.lineHeight / 2
            graphics.pose().pushMatrix()
            graphics.pose().translate(centerX.toFloat(), cpsY.toFloat())
            graphics.pose().scale(0.5f, 0.5f)
            val cpsText = ClickInputTracker.getInstance().leftMouse.clicks().toString() + " CPS"
            graphics.pose().translate(-hud.mc.font.width(cpsText) / 2f, 0f)
            io.github.axolotlclient.util.DrawUtil.drawString(graphics, cpsText, 0, 0, stroke.fgColor, hud.shadow.get())
            graphics.pose().popMatrix()
        }),
        RMB_CPS(
            "rmb_cps", Rectangle(27, 36, 26, 17), Minecraft.getInstance().options.keyUse,
            SpecialKeystrokeRenderer { hud, stroke, graphics ->
            val bounds = stroke.bounds
            val centerX = bounds.x() + stroke.offset.x() + bounds.width() / 2
            val y = bounds.y() + stroke.offset.y() + 3
            val nameY = y + bounds.height() / 4 - hud.mc.font.lineHeight / 2
            io.github.axolotlclient.util.DrawUtil.drawCenteredString(
                graphics, hud.mc.font, "RMB", centerX, nameY, stroke.fgColor, hud.shadow.get()
            )
            if (!hud.displayCps.get()) {
                return@SpecialKeystrokeRenderer
            }
            val cpsY = y + bounds.height() * 3 / 4 - hud.mc.font.lineHeight / 2
            graphics.pose().pushMatrix()
            graphics.pose().translate(centerX.toFloat(), cpsY.toFloat())
            graphics.pose().scale(0.5f, 0.5f)
            val cpsText = ClickInputTracker.getInstance().rightMouse.clicks().toString() + " CPS"
            graphics.pose().translate(-hud.mc.font.width(cpsText) / 2f, 0f)
            io.github.axolotlclient.util.DrawUtil.drawString(graphics, cpsText, 0, 0, stroke.fgColor, hud.shadow.get())
            graphics.pose().popMatrix()
        });

        fun interface SpecialKeystrokeRenderer {
            fun render(hud: KeystrokeHud, stroke: KeystrokeHud.Keystroke, graphics: GuiGraphicsExtractor)
        }

        companion object {
            val byId: Map<String, SpecialKeystroke> = values().associateBy { obj: SpecialKeystroke -> obj.id }
        }
    }

    companion object {
        private const val KEYSTROKE_SAVE_FILE_NAME = "keystrokes.json"
        const val DEFAULT_SIZE = 9
        private val NO_LABEL: java.util.function.Supplier<String> =
            java.util.function.Supplier { ChatFormatting.ITALIC.toString() + I18n.get("keystrokes.stroke.custom_renderer") }

        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("kronhud", "keystrokehud")

        @JvmStatic
        fun getMouseKeyBindName(keyBinding: KeyMapping): Optional<String> {
            if (keyBinding.saveString().equals(
                    InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT).getName(), ignoreCase = true
                )
            ) {
                return Optional.of("LMB")
            } else if (keyBinding.saveString().equals(
                    InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT).getName(), ignoreCase = true
                )
            ) {
                return Optional.of("RMB")
            } else if (keyBinding.saveString().equals(
                    InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE).getName(), ignoreCase = true
                )
            ) {
                return Optional.of("MMB")
            }
            return Optional.empty()
        }

        private fun getRectangle(json: Map<String, *>): Rectangle =
            Rectangle(
                (json["x"] as Long).toInt(), (json["y"] as Long).toInt(),
                (json["width"] as Long).toInt(), (json["height"] as Long).toInt()
            )
    }
}
