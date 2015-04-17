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
package org.fedoraproject.p2.installer;

/**
 * XMvn Eclipse Installer OSGi service.
 * 
 * @author Mikolaj Izdebski
 */
public interface EclipseInstaller {
    /**
     * Perform installation of Eclipse artifacts.
     *
     * @param request
     *            The set of requested installation parameters.
     * @return The installation result, containing the OSGi provided
     *         capabilities.
     * @throws Exception
     *             if installation fails
     */
	EclipseInstallationResult performInstallation(
			EclipseInstallationRequest request) throws Exception;
}
