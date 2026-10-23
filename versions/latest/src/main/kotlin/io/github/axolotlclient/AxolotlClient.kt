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

package io.github.axolotlclient

import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.APIOptions
import io.github.axolotlclient.api.StatusUpdateProviderImpl
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.impl.Bridge
import io.github.axolotlclient.modules.ModuleLoader
import io.github.axolotlclient.modules.auth.Auth
import io.github.axolotlclient.modules.blur.HudBlur
import io.github.axolotlclient.modules.blur.MotionBlur
import io.github.axolotlclient.modules.enchantedmc.AutoClick
import io.github.axolotlclient.modules.enchantedmc.AutoFish
import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.CompassWaypointMarkers
import io.github.axolotlclient.modules.hud.gui.hud.PackDisplayHud
import io.github.axolotlclient.modules.hypixel.HypixelMods
import io.github.axolotlclient.modules.inventorySearch.InventorySearch
import io.github.axolotlclient.modules.inventoryTweaks.InventoryTweaks
import io.github.axolotlclient.modules.hitboxes.Hitboxes
import io.github.axolotlclient.modules.legions.CannonTrajectory
import io.github.axolotlclient.modules.legions.ExplosionBoxes
import io.github.axolotlclient.modules.autoutils.AutoArmor
import io.github.axolotlclient.modules.autoutils.AutoEat
import io.github.axolotlclient.modules.autoutils.AutoReconnect
import io.github.axolotlclient.modules.autoutils.AutoTool
import io.github.axolotlclient.modules.autoutils.AutoWalk
import io.github.axolotlclient.modules.chat.ChatPersistence
import io.github.axolotlclient.modules.chat.QuickPhrases
import io.github.axolotlclient.modules.minimap.FullscreenMap
import io.github.axolotlclient.modules.particles.Particles
import io.github.axolotlclient.modules.screenshotUtils.ScreenshotUtils
import io.github.axolotlclient.modules.scrollableTooltips.ScrollableTooltips
import io.github.axolotlclient.modules.wecui.WorldEditCui
import io.github.axolotlclient.util.BadgeFeatureRenderer
import io.github.axolotlclient.util.ChunkBorders
import io.github.axolotlclient.util.FeatureDisabler
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.LightLevelOverlay
import io.github.axolotlclient.util.notifications.Notifications
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry

class AxolotlClient : AxolotlClientCommon(), ClientModInitializer {

    private fun addBuiltinModules() {
        registerModule(HudManager.construct())
        registerModule(HypixelMods.getInstance())
        registerModule(MotionBlur.getInstance())
        registerModule(HudBlur.getInstance())
        registerModule(ScrollableTooltips.getInstance())
        registerModule(InventorySearch.getInstance())
        // Wave 5 inventory tweaks completion: hidden vanilla effect HUD, centered containers,
        // compact stack counts, plus F3+B-style hitboxes on a keybind.
        registerModule(InventoryTweaks.getInstance())
        registerModule(Hitboxes.getInstance())
        registerModule(AutoFish.getInstance())
        registerModule(AutoClick.getInstance())
        registerModule(Particles.getInstance())
        registerModule(ScreenshotUtils.getInstance())
        registerModule(Auth.getInstance())
        registerModule(WorldEditCui.getInstance())
        registerModule(ExplosionBoxes.getInstance())
        // Wave 4 native utilities (QoL tier, plus the limited-tier auto-walk).
        registerModule(AutoEat.getInstance())
        registerModule(AutoTool.getInstance())
        registerModule(AutoArmor.getInstance())
        registerModule(AutoReconnect.getInstance())
        registerModule(AutoWalk.getInstance())
        // Wave 5 chat extras: persistent history sidecar + tiered quick phrases.
        registerModule(ChatPersistence.getInstance())
        registerModule(QuickPhrases.getInstance())
        // Wave 5: cannon trajectory prediction + fullscreen map (large parity items).
        registerModule(CannonTrajectory.getInstance())
        registerModule(FullscreenMap.getInstance())
    }

    private fun addExternalModules() {
        ModuleLoader.loadExternalModules().forEach { this.registerModule(it) }
    }

    override fun onInitializeClient() {
        Bridge.init()

        addBuiltinModules()
        addExternalModules()

        // Feed the common CompassHud waypoint markers from the latest-side WaypointManager.
        CompassWaypointMarkers.init()

        init(Notifications.getInstance())
        API(StatusUpdateProviderImpl())

        Events.END_RESOURCE_RELOAD.register(Runnable {
            val hud = HudManager.getInstance().get(PackDisplayHud.ID) as? PackDisplayHud
            if (hud != null) {
                hud.update()
            }
        })
        FeatureRendererRegistry.register(BadgeFeatureRenderer.TYPE, ::BadgeFeatureRenderer)
        // ImperiumMC server handshake (hello/features channels) — roadmap M5 slice 1.
        io.github.axolotlclient.imperium.ImperiumIntegration.init()
        // Data push channels (legion/boosts/cooldowns) from the plugin.
        io.github.axolotlclient.imperium.ImperiumChannels.init()
        // Local rules enforcement layer (W4-C): outgoing-packet interceptor scoped to ImperiumMC.
        io.github.axolotlclient.imperium.enforcement.EnforcementService.instance().init()
        // Light-level overlay (Photon parity): budgeted per-tick column sampler.
        LightLevelOverlay.init()
        // chunk_borders: own per-frame gizmo window so the lines render in normal frames.
        ChunkBorders.register()
        logger.debug("Debug Output enabled, Logs will be quite verbose!")
        logger.info("AxolotlClient Initialized")
    }

    override val featureDisabler: FeatureDisablerCommon
        get() = FeatureDisabler.getInstance()

    override fun createConfig(): AxolotlClientConfigCommon = io.github.axolotlclient.config.AxolotlClientConfig()

    override val apiOptions: io.github.axolotlclient.api.Options
        get() = APIOptions.getInstance()

    companion object {
        @JvmStatic
        fun config(): io.github.axolotlclient.config.AxolotlClientConfig =
            getInstance().getConfig() as io.github.axolotlclient.config.AxolotlClientConfig
    }
}
