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

package io.github.axolotlclient.config.migration

import com.google.gson.JsonObject
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.config.migration.impl.V10Migration
import io.github.axolotlclient.config.migration.impl.V11Migration
import io.github.axolotlclient.config.migration.impl.V2Migration
import io.github.axolotlclient.config.migration.impl.V3Migration
import io.github.axolotlclient.config.migration.impl.V4Migration
import io.github.axolotlclient.config.migration.impl.V5Migration
import io.github.axolotlclient.config.migration.impl.V6Migration
import io.github.axolotlclient.config.migration.impl.V7Migration
import io.github.axolotlclient.config.migration.impl.V8Migration
import io.github.axolotlclient.config.migration.impl.V9Migration
import net.fabricmc.loader.api.FabricLoader
import java.util.Optional

interface ConfigMigration {

    /**
     * The version this config migration applies to,
     * meaning that it will run for all versions below this one.
     *
     * @return the version number
     */
    fun version(): Int

    /**
     * Migrate the config
     *
     * @param config the config (input+output; mutable)
     */
    fun apply(config: JsonObject)

    /*
     * Gson utility methods
     */
    fun getObject(element: JsonObject, name: String): Optional<JsonObject> {
        if (element.has(name)) {
            val obj = element.get(name)
            if (obj.isJsonObject) {
                return Optional.of(obj.asJsonObject)
            }
        }
        return Optional.empty()
    }

    fun getOrAddObject(element: JsonObject, name: String): JsonObject {
        if (element.has(name)) {
            val obj = element.get(name)
            if (obj.isJsonObject) {
                return element.get(name).asJsonObject
            }
        }
        val obj = JsonObject()
        element.add(name, obj)
        return obj
    }

    fun getString(element: JsonObject, name: String): Optional<String> {
        if (element.has(name)) {
            val obj = element.get(name)
            if (obj.isJsonPrimitive) {
                return Optional.of(obj.asString)
            }
        }
        return Optional.empty()
    }

    fun getBooleanOrFalse(element: JsonObject, name: String): Boolean {
        if (element.has(name)) {
            val b = element.get(name)
            if (b.isJsonPrimitive) {
                return b.asBoolean
            }
        }
        return false
    }

    companion object {
        const val CONFIG_VERSION: Int = 11

        private val MIGRATIONS: MutableList<ConfigMigration> = arrayListOf(
            V2Migration(),
            V3Migration(),
            V4Migration(),
            V5Migration(),
            V6Migration(),
            V7Migration(),
            V8Migration(),
            V9Migration(),
            V10Migration(),
            V11Migration()
        )

        @JvmStatic
        fun apply(oldVersion: Int, config: JsonObject) {
            MIGRATIONS.sortWith(Comparator.comparingInt { it.version() })
            val logger = AxolotlClientCommon.getInstance().logger

            val devEnv = FabricLoader.getInstance().isDevelopmentEnvironment
            if (devEnv) {
                logger.info("Applying config migrations to update from {} to {}", oldVersion, CONFIG_VERSION)
            }
            // We cannot use the option as it hasn't been set at this point yet.
            val debug = config.get("general")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("debugLogOutput")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            for (migration in MIGRATIONS) {
                if (oldVersion < migration.version()) {
                    if (devEnv) {
                        logger.info("Applying config migration ->{}", migration.version())
                    }
                    migration.apply(config)
                } else if (devEnv && debug) {
                    logger.info("Skipping config migration ->{}", migration.version())
                }
            }
            if (devEnv) {
                logger.info("Applied config migrations.")
            }
        }
    }
}
