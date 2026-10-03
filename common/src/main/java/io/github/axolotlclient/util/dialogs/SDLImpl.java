/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util.dialogs;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.github.axolotlclient.AxolotlClientCommon;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class SDLImpl implements DialogProvider {

	@Override
	public CompletableFuture<Optional<Path>> save(String title, Path origin, String filter) {
		var future = new CompletableFuture<Optional<Path>>();
		try (var stack = MemoryStack.stackPush()) {
			var filters = SDL_DialogFileFilter.create(filter != null ? 1 : 0);
			if (filter != null) {
				filters.name(stack.UTF8("*" + filter)).pattern(stack.UTF8(filter.charAt(0) == '.' ? filter.substring(1) : filter));
			}
			SDLDialog.SDL_ShowSaveFileDialog((userdata, filelist, filter1) -> {
				if (filelist == 0) {
					future.complete(Optional.empty());
					return;
				}
				var deref = MemoryUtil.memGetAddress(filelist);
				if (deref == 0) {
					future.complete(Optional.empty());
					return;
				}

				var str = MemoryUtil.memUTF8(deref);
				future.complete(Optional.of(Path.of(str)));
			}, 0L, 0L, filters, origin.toString());
		}
		return future;
	}

	@Override
	public CompletableFuture<Optional<Path>> open(String title, Path origin, String filter) {
		var future = new CompletableFuture<Optional<Path>>();
		try (var stack = MemoryStack.stackPush()) {
			var filters = SDL_DialogFileFilter.create(filter != null ? 1 : 0);
			if (filter != null) {
				filters.name(stack.UTF8("*" + filter)).pattern(stack.UTF8(filter.charAt(0) == '.' ? filter.substring(1) : filter));
			}
			SDLDialog.SDL_ShowOpenFileDialog((userdata, filelist, filter1) -> {
				if (filelist == 0) {
					future.complete(Optional.empty());
					return;
				}
				var deref = MemoryUtil.memGetAddress(filelist);
				if (deref == 0) {
					future.complete(Optional.empty());
					return;
				}

				var str = MemoryUtil.memUTF8(deref);
				future.complete(Optional.of(Path.of(str)));
			}, 0L, 0L, filters, origin.toString(), false);
		}
		return future;
	}

	@Override
	public CompletableFuture<Optional<List<Path>>> openMultiple(String title, Path origin, String filter) {
		var future = new CompletableFuture<Optional<List<Path>>>();
		try (var stack = MemoryStack.stackPush()) {
			var filters = SDL_DialogFileFilter.create(filter != null ? 1 : 0);
			if (filter != null) {
				filters.name(stack.UTF8("*" + filter)).pattern(stack.UTF8(filter.charAt(0) == '.' ? filter.substring(1) : filter));
			}
			SDLDialog.SDL_ShowOpenFileDialog((userdata, filelist, filter1) -> {
				if (filelist == 0) {
					AxolotlClientCommon.getInstance().getLogger().warn(SDLError.SDL_GetError());
					future.complete(Optional.empty());
					return;
				}
				var deref = MemoryUtil.memGetAddress(filelist);
				if (deref == 0) {
					future.complete(Optional.empty());
					return;
				}

				var str = MemoryUtil.memUTF8(deref);
				future.complete(Optional.of(Arrays.stream(str.split("\0")).map(Path::of).toList()));
			}, 0L, 0L, filters, origin.toString(), true);
		}
		return future;
	}
}
