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

package io.github.axolotlclient.modules.auth.skin;

import java.util.concurrent.CompletableFuture;

import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.auth.Account;
import io.github.axolotlclient.modules.auth.Auth;
import io.github.axolotlclient.modules.auth.MSApi;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class SkinWidget extends AbstractWidget {
	private static final float MODEL_HEIGHT = 2.125F;
	private static final float FIT_SCALE = 0.97F;
	private static final float ROTATION_SENSITIVITY = 2.5F;
	private static final float DEFAULT_ROTATION_X = -5.0F;
	private static final float DEFAULT_ROTATION_Y = 30.0F;
	private static final float ROTATION_X_LIMIT = 50.0F;
	private float rotationX = DEFAULT_ROTATION_X;
	@Setter
	private float rotationY = DEFAULT_ROTATION_Y;
	@Getter
	@Setter
	private Skin skin;
	@Getter
	@Setter
	private Cape cape;
	private final Account owner;
	private boolean noCape, noCapeActive;

	public SkinWidget(int width, int height, Skin skin, @Nullable Cape cape, Account owner) {
		super(0, 0, width, height, CommonComponents.EMPTY);
		this.skin = skin;
		this.cape = cape;
		this.owner = owner;
	}

	public SkinWidget(int width, int height, Skin skin, Account owner) {
		this(width, height, skin, null, owner);
	}

	public void noCape(boolean noCapeActive) {
		noCape = true;
		this.noCapeActive = noCapeActive;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
		var minecraft = Minecraft.getInstance();

		float scale = FIT_SCALE * this.getHeight() / MODEL_HEIGHT;
		float pivotY = -1.0625F;

		SkinManager skinManager = Auth.getInstance().getSkinManager();
		AxoIdentifier skinRl = skinManager.loadSkin(skin);
		boolean classic = skin.classicVariant();
		var capeRl = cape == null ? null : skinManager.loadCape(cape);

		// You might say that using `hashCode()` like this isn't ideal, but in reality it doesn't matter. These objects get freed
		// correctly by the screen so we mostly only need unique identifiers per widget which `hashCode()` provides.
		var renderer = SkinRenderer.getOrCreate(minecraft, "" + hashCode());
		guiGraphicsExtractor.guiRenderState
			.addPicturesInPictureState(
				new SkinRenderState(classic, (Identifier) skinRl, (Identifier) capeRl, this.rotationX, this.rotationY, pivotY, this.getX(), this.getY(), this.getRight(), this.getBottom(), scale, guiGraphicsExtractor.scissorStack.peek(), renderer));
	}

	@Override
	protected void onDrag(MouseButtonEvent mouseButtonEvent, double dragX, double dragY) {
		this.rotationX = Mth.clamp(this.rotationX - (float) dragY * ROTATION_SENSITIVITY, -ROTATION_X_LIMIT, ROTATION_X_LIMIT);
		this.rotationY += (float) dragX * ROTATION_SENSITIVITY;
	}

	@Override
	public void playDownSound(SoundManager handler) {
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}

	@Nullable
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}

	public boolean isEquipped() {
		return noCape ? noCapeActive : (cape != null ? cape.active() : skin != null && skin.active());
	}

	public CompletableFuture<MSApi.MCProfile> equip() {
		var msApi = Auth.getInstance().getMsApi();
		if (noCape) {
			return msApi.hideCape(owner);
		}
		if (cape != null) {
			return cape.equip(msApi, owner);
		}
		if (skin != null) {
			return skin.equip(msApi, owner);
		}
		return msApi.resetSkin(owner);
	}

	public Asset getFocusedAsset() {
		return noCape ? null : cape != null ? cape : skin;
	}
}
