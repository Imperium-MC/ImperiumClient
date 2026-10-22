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

package io.github.axolotlclient.bridge.mixin.resource

import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.bridge.resource.AxoResourceManager
import io.github.axolotlclient.bridge.resource.AxoResource
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceProvider
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import java.util.function.Predicate

@Mixin(ResourceManager::class)
interface ResourceManagerMixin : ResourceProvider, AxoResourceManager {
	@Shadow
	fun listResources(path: String, filter: ResourceManager.Selector): Map<Identifier, Resource>

	@Suppress("UNCHECKED_CAST")
	override fun `br$listResources`(namespace: String, prefix: String, filter: Predicate<AxoIdentifier>): Map<AxoIdentifier, AxoResource> {
		// this cast is maybe not ideal
		return listResources(prefix) { id: Identifier -> filter.test(id) } as Map<AxoIdentifier, AxoResource>
	}

	override fun `br$getResource`(loc: AxoIdentifier): AxoResource {
		return getResourceOrThrow(loc as Identifier)
	}
}
