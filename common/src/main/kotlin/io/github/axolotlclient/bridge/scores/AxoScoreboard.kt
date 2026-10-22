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

package io.github.axolotlclient.bridge.scores

import io.github.axolotlclient.bridge.internal.BridgeUtil
import io.github.axolotlclient.bridge.internal.RequiresImpl

interface AxoScoreboard {
	// @JvmWildcard keeps the Java-generic signature Collection<? extends AxoScoreboardScore>
	// the mixins override with (a bare `out` projection is dropped from JVM signatures in
	// return position).
	@RequiresImpl
	fun `br$getScores`(objective: AxoObjective): Collection<@JvmWildcard AxoScoreboardScore> {
		throw BridgeUtil.noImpl()
	}

	// Nullable: the latest impl returns getPlayerTeam(s), which is null when the member has no team.
	@RequiresImpl
	fun `br$getTeamOfMember`(s: String): AxoTeam? {
		throw BridgeUtil.noImpl()
	}

	fun `br$getTeam`(s: String): AxoTeam? = `br$getTeamOfMember`(s)
}
