/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.config.screen

import io.github.axolotlclient.AxolotlClientConfig.impl.ui.RecreatableScreen
import io.github.axolotlclient.config.profiles.Profiles
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ProfilesScreen(private val parent: Screen?) :
    Screen(Component.translatable("profiles.configure.list")), RecreatableScreen {

    private val haL = HeaderAndFooterLayout(this)
    private var profilesList: ProfilesList? = null
    private var initialized = false

    override fun init() {
        if (!initialized) {
            initialized = true
            haL.addTitleHeader(title, font)
            val list = ProfilesList(minecraft, 0, 0, 0, 25)
            profilesList = list
            haL.addToContents(list)
            val footer = haL.addToFooter(LinearLayout.horizontal()).spacing(4)
            footer.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())
            haL.visitWidgets(this::addRenderableWidget)
        }

        repositionElements()
    }

    override fun repositionElements() {
        haL.arrangeElements()
        profilesList!!.updateSize(this.width, this.haL)
    }

    override fun onClose() {
        Profiles.getInstance().saveProfiles()
        minecraft.gui.setScreen(parent)
    }

    override fun recreate(): Screen {
        return ProfilesScreen(RecreatableScreen.tryRecreate(parent))
    }

    @Environment(EnvType.CLIENT)
    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    class SpacerEntry : Entry() {

        override fun narratables(): List<NarratableEntry> {
            return emptyList()
        }

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
        }

        override fun children(): List<GuiEventListener> {
            return emptyList()
        }

        override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
            return null
        }
    }

    inner class ProfilesList(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int
    ) : ContainerObjectSelectionList<ProfilesScreen.Entry>(minecraft, width, height, y, itemHeight) {

        private val spacer: ProfilesScreen.Entry = SpacerEntry()
        private val add: ProfilesScreen.Entry = NewEntry()

        init {
            reload()
        }

        fun reload() {
            clearEntries()
            Profiles.getInstance().iterateAvailable { p -> addEntry(ProfileEntry(p)) }
            addEntry(spacer)
            addEntry(add)
            val presets = Profiles.getInstance().findPresets()
            if (presets.isNotEmpty()) {
                addEntry(spacer)
                presets.forEach { p -> addEntry(PresetEntry(p)) }
            }
        }

        override fun getRowWidth(): Int {
            return 340
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            children().forEach { e ->
                if (e is ProfileEntry) {
                    e.profileName.setFocused(false)
                }
            }
            return super.mouseClicked(event, doubleClick)
        }

        @Environment(EnvType.CLIENT)
        inner class ProfileEntry(private val profile: Profiles.Profile) : ProfilesScreen.Entry() {

            val profileName: EditBox = EditBox(font, 0, 0, 150, 20, Component.empty())

            val exportButton: Button
            val loadButton: Button
            val duplicateButton: Button
            val removeButton: Button

            init {
                profileName.setValue(profile.name())
                profileName.setResponder { profile.name = it }
                exportButton = Button.builder(EXPORT_BUTTON_TITLE) { btn ->
                    btn.active = false
                    Profiles.getInstance().exportProfile(profile).thenRun { btn.active = true }
                }.width(50).build()
                loadButton = Button.builder(LOAD_BUTTON_TITLE) {
                    Profiles.getInstance().switchTo(profile)
                }.width(50).build()
                duplicateButton = Button.builder(DUPLICATE_BUTTON_TITLE) { b ->
                    val dup = Profiles.getInstance().duplicate(profile)
                    val d = this@ProfilesList.maxScrollAmount() - this@ProfilesList.scrollAmount()
                    @Suppress("UNCHECKED_CAST")
                    val entries = ArrayList(children() as List<ProfilesScreen.Entry>)
                    entries.add(entries.indexOf(this@ProfileEntry) + 1, ProfileEntry(dup))
                    this@ProfilesList.replaceEntries(entries)
                    this@ProfilesList.setScrollAmount(this@ProfilesList.maxScrollAmount() - d)
                }.width(50).build()

                this.removeButton = Button.builder(REMOVE_BUTTON_TITLE) { b ->
                    removeEntry(this@ProfileEntry)
                    Profiles.getInstance().remove(profile)
                    refreshScrollAmount()
                }.width(50).build()
            }

            override fun extractContent(
                guiGraphicsExtractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovering: Boolean,
                partialTick: Float
            ) {
                var i = scrollBarX() - removeButton.width - 4
                val j = contentY - 2
                this.removeButton.setPosition(i, j)
                this.removeButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)

                i -= duplicateButton.width
                duplicateButton.setPosition(i, j)
                duplicateButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)

                val current = Profiles.getInstance().getCurrent() === profile
                loadButton.setMessage(if (current) CURRENT_TEXT else LOAD_BUTTON_TITLE)
                val active = !current
                loadButton.active = active
                removeButton.active = active
                i -= loadButton.width
                this.loadButton.setPosition(i, j)
                this.loadButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                i -= exportButton.width
                exportButton.setPosition(i, j)
                exportButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                profileName.setWidth(i - contentX - 4)
                profileName.setPosition(contentX, j)
                profileName.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            }

            override fun children(): List<GuiEventListener> {
                return listOf(profileName, exportButton, this.loadButton, duplicateButton, removeButton)
            }

            override fun narratables(): List<NarratableEntry> {
                return listOf(profileName, exportButton, this.loadButton, duplicateButton, removeButton)
            }
        }

        inner class NewEntry : ProfilesScreen.Entry() {

            val addButton: Button
            val importButton: Button

            init {
                this.addButton = Button.builder(Component.translatable("profiles.profile.add")) { button ->
                    val entries = ArrayList(this@ProfilesList.children())
                    entries.add(
                        maxOf(entries.indexOf(this) - 1, 0),
                        ProfileEntry(Profiles.getInstance().newProfile(I18n.get("profiles.profile.default_new_name")))
                    )
                    this@ProfilesList.replaceEntries(entries)
                    Profiles.getInstance().saveProfiles()
                    this@ProfilesList.setScrollAmount(this@ProfilesList.maxScrollAmount().toDouble())
                }.bounds(0, 0, 150, 20)
                    .build()
                this.importButton = Button.builder(Component.translatable("profiles.profile.import")) { btn ->
                    btn.active = false
                    Profiles.getInstance().importProfiles().thenRun {
                        btn.active = true
                        this@ProfilesList.reload()
                    }
                }.build()
            }

            override fun narratables(): List<NarratableEntry> {
                return listOf(addButton, importButton)
            }

            override fun extractContent(
                guiGraphicsExtractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovering: Boolean,
                partialTick: Float
            ) {
                var i = scrollBarX() - contentWidth / 2 - 10 - addButton.width + 2
                val j = contentY - 2
                this.addButton.setPosition(i, j)
                this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                this.importButton.setPosition(addButton.right + 2, j)
                this.importButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            }

            override fun children(): List<GuiEventListener> {
                return listOf(addButton, importButton)
            }
        }

        inner class PresetEntry(preset: Profiles.ProfilePreset) : ProfilesScreen.Entry() {

            val importButton: Button
            val label: StringWidget

            init {
                this.importButton = Button.builder(Component.translatable("profiles.profile.import_preset")) { btn ->
                    preset.importProfile()
                    this@ProfilesList.reload()
                    this@ProfilesList.scrollToEntry(this@PresetEntry)
                }.width(150).build()
                this.label = StringWidget(Component.literal(preset.info.name), font)
                label.setHeight(20)
            }

            override fun narratables(): List<NarratableEntry> {
                return listOf(label, importButton)
            }

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                a: Float
            ) {
                var i = scrollBarX() - importButton.width - 4
                val j = contentY - 2
                importButton.setPosition(i, j)
                importButton.extractRenderState(graphics, mouseX, mouseY, a)
                i -= label.width
                label.setMaxWidth(i - contentX - 4, StringWidget.TextOverflow.SCROLLING)
                label.setPosition(contentX, j)
                label.extractRenderState(graphics, mouseX, mouseY, a)
            }

            override fun children(): List<GuiEventListener> {
                return listOf(label, importButton)
            }
        }
    }

    companion object {
        // Static constants of the (Java) nested ProfileEntry class; inner Kotlin classes
        // cannot own companion objects, so they live one level up on the screen.
        private val EXPORT_BUTTON_TITLE: Component = Component.translatable("profiles.profile.export")
        private val CURRENT_TEXT: Component = Component.translatable("profiles.profile.current")
        private val LOAD_BUTTON_TITLE: Component = Component.translatable("profiles.profile.load")
        private val DUPLICATE_BUTTON_TITLE: Component = Component.translatable("profiles.profile.duplicate")
        private val REMOVE_BUTTON_TITLE: Component = Component.translatable("profiles.profile.remove")
    }
}
