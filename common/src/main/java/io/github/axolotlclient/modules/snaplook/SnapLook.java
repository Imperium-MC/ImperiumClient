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

package io.github.axolotlclient.modules.snaplook;

import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.modules.AbstractCommonModule;
import io.github.axolotlclient.modules.freelook.Freelook;
import io.github.axolotlclient.util.options.ForceableBooleanOption;

/**
 * Snap Look (Alpine parity): hold a key to instantly snap the camera 180 degrees
 * (look behind you); releasing the key returns to the exact pre-snap view.
 *
 * <p>This is a sibling of {@link Freelook} that reuses its camera rotation path
 * (the {@code CameraMixin} yaw hooks and the {@code EntityMixin} rotation
 * intercept) instead of adding its own mixins. While snapping, mouse rotation is
 * consumed and discarded, so the player entity never turns; that is what makes
 * the release restore the exact pre-snap yaw without any stored camera state.
 * Pitch is never modified.</p>
 *
 * <p>Composition with Freelook: while a Freelook is actively overriding the
 * camera (freelook mode + active), this module defers entirely, so holding both
 * keys cannot corrupt the view. If the snap key is still held when Freelook
 * releases, the snap activates on the next tick.</p>
 */
public class SnapLook extends AbstractCommonModule {
	private static final SnapLook INSTANCE = new SnapLook();
	private static final AxoKeybinding KEY = AxoKeybinding.create(null, "key.snaplook");
	public final ForceableBooleanOption enabled = new ForceableBooleanOption("enabled", false);
	private final OptionCategory category = OptionCategory.create("snaplook");
	private final BooleanOption returnOnRelease = new BooleanOption("returnOnRelease", true);
	private boolean active;

	public static SnapLook getInstance() {
		return INSTANCE;
	}

	@Override
	public void init() {
		category.add(enabled, returnOnRelease);
		AxolotlClientCommon.getInstance().getConfig().addCategory(category);
		Events.DISCONNECT.register(() -> active = false);
	}

	@Override
	public void tick() {
		if (!enabled.get() || client.br$getScreen() != null) return;

		if (returnOnRelease.get()) {
			if (KEY.br$isPressed()) {
				if (!active) {
					start();
				}
			} else if (active) {
				stop();
			}
		} else if (KEY.br$consumeClick()) {
			if (active) {
				stop();
			} else {
				start();
			}
		}
	}

	private void start() {
		active = true;
		client.br$notifyLevelRenderer();
	}

	private void stop() {
		active = false;
		client.br$notifyLevelRenderer();
	}

	/**
	 * Mirrors {@link Freelook#consumeRotation(double, double)} in the entity
	 * rotation intercept: while snapping, the mouse deltas are consumed (and
	 * discarded) so the entity never turns and the snapped view stays locked.
	 */
	public boolean consumeRotation() {
		return isSnapping();
	}

	/**
	 * Applies the 180-degree offset to a camera yaw from the same hooks Freelook
	 * overrides. No-op while an active Freelook (freelook mode) owns the camera.
	 */
	public float yaw(float defaultYaw) {
		if (!isSnapping()) {
			return defaultYaw;
		}

		return defaultYaw + 180;
	}

	public boolean isSnapping() {
		return active && enabled.get() && !freelookOverridesCamera();
	}

	private static boolean freelookOverridesCamera() {
		Freelook freelook = Freelook.getInstance();
		return freelook.enabled.get() && freelook.isActive() && freelook.needsDisabling();
	}
}
