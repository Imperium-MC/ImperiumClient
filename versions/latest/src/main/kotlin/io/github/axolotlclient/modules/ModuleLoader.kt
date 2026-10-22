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

package io.github.axolotlclient.modules

import java.util.ArrayList

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.config.screen.CreditsScreen
import net.fabricmc.loader.api.FabricLoader

object ModuleLoader {
    @JvmStatic
    fun loadExternalModules(): List<AbstractModule> {
        val modules: MutableList<AbstractModule> = ArrayList()
        FabricLoader.getInstance().getEntrypointContainers("axolotlclient.module", AbstractModule::class.java)
            .forEach { entrypoint ->
                try {
                    val module = entrypoint.getEntrypoint()
                    if (module != null) {
                        modules.add(module)
                        val modName = entrypoint.getProvider().metadata.name
                        val data = entrypoint.getProvider().metadata
                        val authorsNContributors: MutableList<String> = ArrayList()

                        val authors = data.authors.stream()
                            .map { obj -> obj.name }
                            .toList()

                        authorsNContributors.add("Author(s):")
                        authorsNContributors.addAll(authors)
                        authorsNContributors.add("")

                        authorsNContributors.add("Contributor(s):")
                        authorsNContributors.addAll(data.contributors.stream().map { obj -> obj.name }.toList())
                        CreditsScreen.externalModuleCredits[modName] = authorsNContributors.toTypedArray()
                    }
                } catch (e: Exception) {
                    AxolotlClientCommon.getInstance().logger.warn(
                        "Skipping module: " + entrypoint.getProvider().metadata.name +
                            " because of error:", e
                    )
                }
            }
        return modules
    }
}
