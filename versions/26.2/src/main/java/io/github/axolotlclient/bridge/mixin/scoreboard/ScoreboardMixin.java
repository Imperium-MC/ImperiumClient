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

package io.github.axolotlclient.bridge.mixin.scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import io.github.axolotlclient.bridge.scores.AxoObjective;
import io.github.axolotlclient.bridge.scores.AxoScoreboard;
import io.github.axolotlclient.bridge.scores.AxoScoreboardScore;
import io.github.axolotlclient.bridge.scores.AxoTeam;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin implements AxoScoreboard {

	@Shadow
	@Nullable
	public abstract PlayerTeam getPlayerTeam(String teamName);

	@Shadow
	public abstract Collection<PlayerScoreEntry> listPlayerScores(Objective objective);

	@Override
	public Collection<? extends AxoScoreboardScore> br$getScores(AxoObjective objective) {
		var entries = new ArrayList<>(listPlayerScores((Objective) objective));
		entries.sort(Comparator.comparing(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
		return entries.reversed();
	}

	@Override
	public AxoTeam br$getTeamOfMember(String s) {
		return getPlayerTeam(s);
	}
}
