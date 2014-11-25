/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author Mikolaj Izdebski
 */
class Plugin {
	private final Set<String> imports = new LinkedHashSet<>();
	private final Set<String> exports = new LinkedHashSet<>();
	private final Set<String> requires = new LinkedHashSet<>();
	private final Manifest mf = new Manifest();
	private final Attributes attr = mf.getMainAttributes();

	public Plugin(String id, String ver) {
		attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attr.put(new Attributes.Name("Bundle-ManifestVersion"), "2");
		attr.put(new Attributes.Name("Bundle-SymbolicName"), id);
		attr.put(new Attributes.Name("Bundle-Version"), ver);
	}

	public String getId() {
		return attr.getValue(new Attributes.Name("Bundle-SymbolicName"));
	}

	public String getVersion() {
		return attr.getValue(new Attributes.Name("Bundle-Version"));
	}

	public Plugin importPackage(String name) {
		imports.add(name);
		return this;
	}

	public Plugin exportPackage(String name) {
		exports.add(name);
		return this;
	}

	public Plugin requireBundle(String name) {
		requires.add(name);
		return this;
	}

	public Plugin addMfEntry(String key, String value) {
		attr.put(new Attributes.Name(key), value);
		return this;
	}

	private void addManifestSet(Attributes attr, String key, Set<String> values) {
		Iterator<String> it = values.iterator();
		if (!it.hasNext())
			return;
		StringBuilder sb = new StringBuilder(it.next());
		while (it.hasNext())
			sb.append(',').append(it.next());
		attr.put(new Attributes.Name(key), sb.toString());
	}

	public void writeBundle(Path path) throws IOException {
		addManifestSet(attr, "Import-Package", imports);
		addManifestSet(attr, "Export-Package", exports);
		addManifestSet(attr, "Require-Bundle", requires);
		try (OutputStream os = Files.newOutputStream(path)) {
			try (OutputStream jos = new JarOutputStream(os, mf)) {
			}
		}
	}
}