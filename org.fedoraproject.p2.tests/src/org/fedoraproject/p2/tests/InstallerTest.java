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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstaller;

/**
 * @author Mikolaj Izdebski
 */
class Plugin {
	private final Set<String> imports = new LinkedHashSet<>();
	private final Set<String> exports = new LinkedHashSet<>();
	private final Set<String> requires = new LinkedHashSet<>();
	private final Manifest mf = new Manifest();
	private final Attributes attr = mf.getMainAttributes();

	public Plugin(String id) {
		attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attr.put(new Attributes.Name("Bundle-ManifestVersion"), "2");
		attr.put(new Attributes.Name("Bundle-SymbolicName"), id);
		attr.put(new Attributes.Name("Bundle-Version"), "1.0.0");
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

interface BuildrootVisitor {
	void visitPlugin(String dropin, String id);

	void visitFeature(String dropin, String id);

	void visitSymlink(String dropin, String id);
}

/**
 * @author Mikolaj Izdebski
 */
public class InstallerTest {
	private final EclipseInstaller installer;
	private Path tempDir;
	private Map<String, Plugin> reactorPlugins;
	private BuildrootVisitor visitor;
	private EclipseInstallationRequest request;
	private Path root;
	private Path reactor;

	public InstallerTest() {
		BundleContext context = Activator.getBundleContext();
		ServiceReference<EclipseInstaller> serviceReference = context
				.getServiceReference(EclipseInstaller.class);
		assertNotNull(serviceReference);
		installer = context.getService(serviceReference);
		assertNotNull(installer);
	}

	@Before
	public void setUp() throws Exception {
		reactorPlugins = new LinkedHashMap<>();
		tempDir = Files.createTempDirectory("fp-p2-");

		visitor = createMock(BuildrootVisitor.class);

		root = tempDir.resolve("root");
		Files.createDirectory(root);
		reactor = tempDir.resolve("reactor");
		Files.createDirectory(reactor);

		request = new EclipseInstallationRequest();
		request.setBuildRoot(root);
		request.setTargetDropinDirectory(Paths.get("dropins"));
		request.setMainPackageId("main");
	}

	@After
	public void tearDown() throws Exception {
		delete(tempDir);
	}

	private void delete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
			for (Path child : Files.newDirectoryStream(path))
				delete(child);

		Files.delete(path);
	}

	private Plugin addPlugin(String id, Map<String, Plugin> map) {
		Plugin plugin = map.get(id);
		if (plugin == null) {
			plugin = new Plugin(id);
			map.put(id, plugin);
		}
		return plugin;
	}

	public Plugin addReactorPlugin(String id) {
		return addPlugin(id, reactorPlugins);
	}

	public void performTest() throws Exception {
		// Create reactor plugins
		for (Entry<String, Plugin> entry : reactorPlugins.entrySet()) {
			String id = entry.getKey();
			Plugin plugin = entry.getValue();
			Path path = reactor.resolve(id + ".jar");
			plugin.writeBundle(path);
			request.addPlugin(path);
		}

		installer.performInstallation(request);

		replay(visitor);
		visitDropins(root.resolve("dropins"));
		verify(visitor);
	}

	private void visitDropins(Path dropins) throws Exception {
		assertTrue(Files.isDirectory(dropins, LinkOption.NOFOLLOW_LINKS));

		for (Path dropinPath : Files.newDirectoryStream(dropins)) {
			assertTrue(Files.isDirectory(dropinPath, LinkOption.NOFOLLOW_LINKS));
			String dropin = dropinPath.getFileName().toString();

			for (Path dropinSubdir : Files.newDirectoryStream(dropinPath)) {
				assertTrue(Files.isDirectory(dropinSubdir,
						LinkOption.NOFOLLOW_LINKS));
				assertEquals("eclipse", dropinSubdir.getFileName().toString());

				for (Path categoryPath : Files.newDirectoryStream(dropinSubdir)) {
					assertTrue(Files.isDirectory(categoryPath,
							LinkOption.NOFOLLOW_LINKS));
					String cat = categoryPath.getFileName().toString();
					boolean isPlugin = cat.equals("plugins");
					boolean isFeature = cat.equals("features");
					assertTrue(isPlugin ^ isFeature);

					for (Path unit : Files.newDirectoryStream(categoryPath)) {
						String name = unit.getFileName().toString();
						boolean isDir = Files.isDirectory(unit);
						boolean isLink = Files.isSymbolicLink(unit);
						// Either dir-shaped or ends with .jar
						assertTrue(isDir ^ name.endsWith(".jar"));
						// While theoretically possible, symlinks to
						// directory-shaped units are not expected
						assertFalse(isLink && isDir);
						// We never symlink features
						assertFalse(isFeature && isLink);
						String id = name.replaceAll("_.*", "");
						if (isLink)
							visitor.visitSymlink(dropin, id);
						else if (isPlugin)
							visitor.visitPlugin(dropin, id);
						else if (isFeature)
							visitor.visitFeature(dropin, id);
						else
							fail();
					}
				}
			}
		}
	}

	public void expectPlugin(String dropin, String plugin) {
		visitor.visitPlugin(dropin, plugin);
		expectLastCall();
	}

	public void expectFeature(String dropin, String plugin) {
		visitor.visitFeature(dropin, plugin);
		expectLastCall();
	}

	public void expectSymlink(String dropin, String plugin) {
		visitor.visitSymlink(dropin, plugin);
		expectLastCall();
	}

	// The simplest case possible: one plugin with no deps
	@Test
	public void simpleTest() throws Exception {
		addReactorPlugin("foo");
		expectPlugin("main", "foo");
		performTest();
	}

	// Two plugins with no deps
	@Test
	public void twoPluginsTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		expectPlugin("main", "foo");
		expectPlugin("main", "bar");
		performTest();
	}

	// Directory-shaped plugin
	@Test
	public void dirShapedPlugin() throws Exception {
		addReactorPlugin("foo").addMfEntry("Eclipse-BundleShape", "dir");
		expectPlugin("main", "foo");
		performTest();
		Path dir = root.resolve("dropins/main/eclipse/plugins/foo_1.0.0");
		assertTrue(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS));
	}

	// Two plugins manually assigned to subpackages, third implicitly installed
	// to main pkg
	@Test
	public void subpackageSplitTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		addReactorPlugin("baz");
		request.addPackageMapping("foo", "sub1");
		request.addPackageMapping("bar", "sub2");
		expectPlugin("sub1", "foo");
		expectPlugin("sub2", "bar");
		expectPlugin("main", "baz");
		performTest();
	}

	// Plugin B is required by A, hence it is getting installed in subpackage
	// together with A.
	@Test
	public void interdepSplitTest() throws Exception {
		addReactorPlugin("A").requireBundle("B");
		addReactorPlugin("B");
		addReactorPlugin("C");
		request.addPackageMapping("A", "sub");
		expectPlugin("sub", "A");
		expectPlugin("sub", "B");
		expectPlugin("main", "C");
		performTest();
	}

	// Plugins B1,B2 are required by both A and C, which are installed to
	// different supackages. Installer puts B1 next to A and C, but puts B2 in
	// different package because this was explicitly requested by user. B3 lands
	// in main package as it's not required by anything and not explicitly put
	// in any package by user.
	@Test
	public void interdepCommonTest() throws Exception {
		addReactorPlugin("A").requireBundle("B1").requireBundle("B2");
		addReactorPlugin("B1");
		addReactorPlugin("B2");
		addReactorPlugin("B3");
		addReactorPlugin("C").requireBundle("B2").requireBundle("B1");
		request.addPackageMapping("A", "sub");
		request.addPackageMapping("C", "sub");
		request.addPackageMapping("B2", "different");
		expectPlugin("sub", "A");
		expectPlugin("sub", "C");
		expectPlugin("sub", "B1");
		expectPlugin("different", "B2");
		expectPlugin("main", "B3");
		performTest();
	}

	// Plugin B is required by both A and C, which are installed to different
	// subpackages. Installation fails as installer cannot guess where to
	// install B.
	@Test(expected = RuntimeException.class)
	public void interdepImpossibleSplitTest() throws Exception {
		addReactorPlugin("A").requireBundle("B");
		addReactorPlugin("B");
		addReactorPlugin("C").requireBundle("B");
		request.addPackageMapping("A", "sub1");
		request.addPackageMapping("C", "sub2");
		performTest();
	}

	// One bundle which has dependency on two external libs, one through
	// Require-Bundle and one through Import-Package. Both libs are expected to
	// be symlinked next to our plugin.
	@Test
	public void symlinkTest() throws Exception {
		Plugin myPlugin = addReactorPlugin("my-plugin");
		myPlugin.requireBundle("org.apache.commons.io");
		myPlugin.importPackage("org.apache.commons.lang");
		expectPlugin("main", "my-plugin");
		expectSymlink("main", "org.apache.commons.io");
		expectSymlink("main", "org.apache.commons.lang");
		performTest();
	}

	// Plugin which directly depends on junit. Besides junit, hamcrest is
	// expected to be symlinked too as junit depends on hamcrest.
	@Test
	public void transitiveSymlinkTest() throws Exception {
		addReactorPlugin("my.tests").importPackage("junit.framework");
		expectPlugin("main", "my.tests");
		expectSymlink("main", "org.junit");
		expectSymlink("main", "org.hamcrest.core");
		performTest();
	}

	// Two independant plugins, both require junit. Junit and hamcrest must be
	// symlinked next to both plugins.
	@Test
	public void indepPluginsCommonDep() throws Exception {
		addReactorPlugin("A").importPackage("junit.framework");
		addReactorPlugin("B").requireBundle("org.junit");
		request.addPackageMapping("A", "pkg1");
		request.addPackageMapping("B", "pkg2");
		expectPlugin("pkg1", "A");
		expectSymlink("pkg1", "org.junit");
		expectSymlink("pkg1", "org.hamcrest.core");
		expectPlugin("pkg2", "B");
		expectSymlink("pkg2", "org.junit");
		expectSymlink("pkg2", "org.hamcrest.core");
		performTest();
	}

	// FIXME this doesn't work currently
	@Ignore
	// Two plugins A and B, where B requires A. Both require junit. Junit and
	// hamcrest are symlinked only next to A.
	@Test
	public void depPluginsCommonDep() throws Exception {
		addReactorPlugin("A").importPackage("junit.framework");
		addReactorPlugin("B").requireBundle("org.junit").requireBundle("A");
		request.addPackageMapping("A", "pkg1");
		request.addPackageMapping("B", "pkg2");
		expectPlugin("pkg1", "A");
		expectSymlink("pkg1", "org.junit");
		expectSymlink("pkg1", "org.hamcrest.core");
		expectPlugin("pkg2", "B");
		performTest();
	}
}