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

package io.github.axolotlclient.modules.tnttime;

import java.text.DecimalFormat;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.util.AxoText;
import io.github.axolotlclient.modules.AbstractCommonModule;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import lombok.Getter;

public class TntTime extends AbstractCommonModule {
	@Getter
	private static final TntTime instance = new TntTime();

	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	private final OptionCategory category = OptionCategory.create("tnttime");
	private final IntegerOption decimalPlaces = new IntegerOption("decimalplaces", 2, 0, 6);
	private DecimalFormat format;
	private int decimals;

	// Fuse colour stages (Alpine profile M6): dynamic_color off pins the text to the
	// style's regular colour; on (default) the stage colours below apply.
	private final BooleanOption dynamicColor = new BooleanOption("tnttime.dynamic_color", true);
	private final ColorOption earlyFuseColor = new ColorOption("tnttime.early_fuse_color", new Color(255, 255, 255).immutable());
	private final ColorOption warningFuseColor = new ColorOption("tnttime.warning_fuse_color", new Color(255, 255, 85).immutable());
	private final ColorOption criticalFuseColor = new ColorOption("tnttime.critical_fuse_color", new Color(255, 85, 85).immutable());

	@Override
	public void init() {
		category.add(enabled, decimalPlaces, dynamicColor, earlyFuseColor, warningFuseColor, criticalFuseColor);
		AxolotlClientCommon.getInstance().getConfig().rendering.add(category);
	}

	@Override
	public void tick() {
		if (decimalPlaces.get() != decimals || format == null) {
			StringBuilder string = new StringBuilder("#0");
			if (decimalPlaces.get() > 0) {
				string.append(".");
				string.append("0".repeat(Math.max(0, decimalPlaces.get())));
			}
			format = new DecimalFormat(string.toString());
			decimals = decimalPlaces.get();
		}
	}

	public AxoText getFuseTime(float time) {
		float secs = time / 20F;
		AxoText.Mutable text = AxoText.literal(String.valueOf(format.format(secs)));
		if (dynamicColor.get()) {
			text.br$color(getCurrentColor(secs));
		}
		return text;
	}

	/**
	 * Fuse colour stages: the early colour while more than 5 seconds remain, the warning
	 * colour from 5 down to 3, the critical colour for the last 3 seconds.
	 */
	private io.github.axolotlclient.AxolotlClientConfig.api.util.Color getCurrentColor(float seconds) {
		if (seconds > 5d) {
			return earlyFuseColor.get();
		} else if (seconds > 3d) {
			return warningFuseColor.get();
		} else {
			return criticalFuseColor.get();
		}
	}
}
