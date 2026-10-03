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

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class TFDImpl implements DialogProvider {
	@Override
	public CompletableFuture<Optional<Path>> save(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (var stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var file = TinyFileDialogs.tinyfd_saveFileDialog(title, origin.toString(), pointers, null);
				return Optional.ofNullable(file).map(Path::of);
			}
		});
	}

	@Override
	public CompletableFuture<Optional<Path>> open(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var file = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers,
					null, false
				);
				return Optional.ofNullable(file).map(Path::of);
			}
		});
	}

	@Override
	public CompletableFuture<Optional<List<Path>>> openMultiple(String title, Path origin, String filter) {
		return CompletableFuture.supplyAsync(() -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var pointers = stack.mallocPointer(1);
				pointers.put(stack.UTF8("*"+filter));
				pointers.flip();
				var files = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers,
					null, true
				);
				return Optional.ofNullable(files).map(s -> Arrays.stream(s.split("\\|")).map(Path::of).toList());
			}
		});
	}
}
