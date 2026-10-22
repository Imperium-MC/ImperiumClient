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

import java.io.IOException
import java.nio.file.Files

import com.google.common.base.Preconditions
import io.github.axolotlclient.AxolotlClientConfig.api.AxolotlClientConfig
import io.github.axolotlclient.AxolotlClientConfig.api.ui.ConfigUI
import io.github.axolotlclient.AxolotlClientConfig.impl.managers.JsonConfigManager
import io.github.axolotlclient.AxolotlClientConfig.impl.managers.VersionedJsonConfigManager
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.Options
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.util.AxoProfiler
import io.github.axolotlclient.config.migration.ConfigMigration
import io.github.axolotlclient.config.profiles.ProfileAware
import io.github.axolotlclient.config.profiles.Profiles
import io.github.axolotlclient.modules.Module
import io.github.axolotlclient.modules.freelook.Freelook
import io.github.axolotlclient.modules.hud.ClickInputTracker
import io.github.axolotlclient.modules.hypixel.HypixelAbstractionLayer
import io.github.axolotlclient.modules.render.BeaconBeam
import io.github.axolotlclient.modules.rpc.DiscordRPC
import io.github.axolotlclient.modules.snaplook.SnapLook
import io.github.axolotlclient.modules.tnttime.TntTime
import io.github.axolotlclient.modules.zoom.Zoom
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.Logger
import io.github.axolotlclient.util.OSUtil
import io.github.axolotlclient.util.compat.BetterTabCompat
import io.github.axolotlclient.util.notifications.NotificationProvider
import net.fabricmc.loader.api.FabricLoader

abstract class AxolotlClientCommon {
    private var configField: AxolotlClientConfigCommon? = null
    val logger: Logger = Logger.Slf4jLogger()
    private var notificationProviderField: NotificationProvider? = null
    private var configManagerField: JsonConfigManager? = null
    private var initialized = false

    @JvmField
    val modules: MutableList<Module> = ArrayList()

    val notificationProvider: NotificationProvider
        get() {
            Preconditions.checkState(initialized && notificationProviderField != null)
            return notificationProviderField!!
        }

    val configManager: JsonConfigManager
        get() {
            Preconditions.checkState(initialized && configManagerField != null)
            return configManagerField!!
        }

    fun getConfig(): AxolotlClientConfigCommon {
        Preconditions.checkState(initialized && configField != null)
        return configField!!
    }

    private fun addBuiltinCommonModules() {
        registerModule(ClickInputTracker.getInstance())
        registerModule(BeaconBeam.getInstance())
        registerModule(Freelook.getInstance())
        registerModule(SnapLook.getInstance())
        registerModule(TntTime.getInstance())
        registerModule(DiscordRPC.getInstance())
        registerModule(apiOptions)
        registerModule(Zoom.getInstance())
        registerModule(BetterTabCompat())
    }

    // init logic

    private fun earlyModuleInit() {
        modules.forEach(Module::init)
    }

    private fun lateModuleInit() {
        modules.forEach(Module::lateInit)
    }

    private fun initConfig() {
        val configFile = mainConfigFile
        if (Files.notExists(configFile)) {
            val legacy = arrayOf(resolveConfigFile("axolotlclient.json"))
            for (p in legacy) {
                try {
                    if (Files.exists(p)) {
                        Files.move(p, configFile)
                        break
                    }
                } catch (e: IOException) {
                    logger.warn("Failed to move config file, it might get reset! ", e)
                }
            }
        }
        configManagerField = VersionedJsonConfigManager(
            configFile, getConfig().config, ConfigMigration.CONFIG_VERSION
        ) { oldVersion, _, _, json ->
            ConfigMigration.apply(oldVersion.major, json)
            json
        }

        AxolotlClientConfig.getInstance().register(configManagerField)
        configManagerField!!.load()

        configManagerField!!.suppressName("x")
        configManagerField!!.suppressName("y")
        configManagerField!!.suppressName(getConfig().hidden.getName())
    }

    protected fun init(provider: NotificationProvider) {
        Preconditions.checkState(!initialized)
        Preconditions.checkState(instance == null)

        instance = this
        addBuiltinCommonModules()

        initialized = true
        Profiles.getInstance().loadProfiles()

        this.notificationProviderField = provider
        configField = createConfig()

        earlyModuleInit()
        initConfig()

        ConfigUI.getInstance().runWhenLoaded(Runnable {
            ConfigUI.getInstance().addWidget("vanilla", "graphics", "io.github.axolotlclient.util.options.vanilla.AxoGraphicsWidget")
            ConfigUI.getInstance().addWidget("rounded", "graphics", "io.github.axolotlclient.util.options.rounded.AxoGraphicsWidget")
            lateModuleInit()
        })

        Events.TICK.register(Runnable {
            AxoProfiler.get().`br$push`("ImperiumClient")
            modules.forEach(Module::tick)
            AxoProfiler.get().`br$pop`()
        })

        featureDisabler.init()

        // register events

        Events.END_RESOURCE_RELOAD.register(Runnable { HypixelAbstractionLayer.getInstance().clearPlayerData() })
        Events.CLIENT_STOP.register(Runnable { API.getInstance().shutdown() })
    }

    protected fun registerModule(module: Module) {
        Preconditions.checkState(!initialized)
        modules.add(module)
    }

    protected abstract val featureDisabler: FeatureDisablerCommon

    protected abstract fun createConfig(): AxolotlClientConfigCommon

    abstract val apiOptions: Options

    // random stuff

    fun saveConfig() {
        configManager.save()
        for (m in modules) {
            if (m is ProfileAware) {
                m.saveConfig()
            }
        }
    }

    val mainConfigFile: java.nio.file.Path
        get() {
            val path = resolveProfileConfigFile("axolotlclient.json")
            try {
                Files.createDirectories(path.parent)
            } catch (e: IOException) {
                logger.warn("Failed to create config directory, config may not be saved correctly!", e)
            }
            return path
        }

    fun reloadConfig() {
        configManager.setFile(mainConfigFile)
        configManager.load()
        for (m in modules) {
            if (m is ProfileAware) {
                m.reloadConfig()
            }
        }
        lateModuleInit()
        API.getInstance().restart()
    }

    companion object {
        const val MODID: String = "axolotlclient"

        // Separate from MODID (which is also the asset namespace) so Imperium Client never reads or moves upstream AxolotlClient's config.
        const val CONFIG_DIR: String = "imperiumclient"

        @JvmField
        val BADGE_PATH: AxoIdentifier = AxoIdentifier.of(MODID, "textures/badge.png")

        // static utility methods
        @JvmStatic
        fun resolveConfigFile(file: String): java.nio.file.Path =
            FabricLoader.getInstance().configDir.resolve(CONFIG_DIR).resolve(file)

        @JvmStatic
        fun resolveProfileConfigFile(file: String): java.nio.file.Path =
            Profiles.getInstance().resolveProfileFile(file)

        @JvmField
        val SHADERS_SUPPORTED: Boolean =
            OSUtil.getOS() != OSUtil.OperatingSystem.OTHER &&
                !FabricLoader.getInstance().isModLoaded("vulkanmod")

        @JvmField
        val VERSION: String = FabricLoader.getInstance()
            .getModContainer("axolotlclient-common")
            .orElseThrow()
            .metadata
            .version
            .friendlyString

        @JvmField
        val GAME_VERSION: String = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .orElseThrow()
            .metadata
            .version
            .friendlyString

        private var instance: AxolotlClientCommon? = null

        @JvmStatic
        fun getInstance(): AxolotlClientCommon {
            Preconditions.checkState(instance != null)
            return instance!!
        }
    }
}
