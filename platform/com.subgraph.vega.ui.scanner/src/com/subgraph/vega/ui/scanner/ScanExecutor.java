/*******************************************************************************
 * Copyright (c) 2011 Subgraph.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Subgraph - initial API and implementation
 ******************************************************************************/
package com.subgraph.vega.ui.scanner;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;

import com.subgraph.vega.api.scanner.IScan;
import com.subgraph.vega.api.model.identity.IIdentity;
import com.subgraph.vega.api.scanner.IScanner;
import com.subgraph.vega.api.scanner.IScannerConfig;
import com.subgraph.vega.ui.scanner.preferences.IPreferenceConstants;
import com.subgraph.vega.ui.scanner.wizards.NewScanWizard;
import com.subgraph.vega.ui.scanner.wizards.NewWizardDialog;

public class ScanExecutor {
	
	public String runScan(Shell shell, String target) {
		final IScanner scanner = Activator.getDefault().getScanner();
		final IScan scan = scanner.createScan();
		final Collection<IIdentity> identities = Activator.getDefault().getModel().getCurrentWorkspace().getIdentityModel().getAllIdentities();

		NewScanWizard wizard = new NewScanWizard(target, identities, scan.getModuleList());
		WizardDialog dialog = new NewWizardDialog(shell, wizard);
		if(dialog.open() == IDialogConstants.OK_ID) {
			if(wizard.isDomTest()) {
				runDomTest();
				return null;
			}
			return maybeLaunchScanFromWizard(shell, wizard, scanner, scan);
		} else {
			// REVISIT lame
			scan.stopScan();
		}
		return null;
	}
	
	private String maybeLaunchScanFromWizard(Shell shell, NewScanWizard wizard, IScanner scanner, IScan scan) {
		URI targetUri = wizard.getScanHostURI();
		if(targetUri == null) {
			return null;
		}

		scanner.lock(scan);
		final IScannerConfig config = scan.getConfig();
		config.setBaseURI(targetUri);
		config.setUserAgent(IPreferenceConstants.P_USER_AGENT);
		config.setCookieList(getCookieList(wizard.getCookieStringList(), targetUri));
//		config.setBasicUsername(wizard.getBasicUsername());
//		config.setBasicPassword(wizard.getBasicPassword());
//		config.setBasicRealm(wizard.getBasicRealm());
//		config.setBasicDomain(wizard.getBasicDomain());
		config.setScanIdentity(wizard.getScanIdentity());
		config.setExclusions(wizard.getExclusions());
		final IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		config.setLogAllRequests(preferences.getBoolean(IPreferenceConstants.P_LOG_ALL_REQUESTS));
		config.setDisplayDebugOutput(preferences.getBoolean(IPreferenceConstants.P_DISPLAY_DEBUG_OUTPUT));
		config.setMaxRequestsPerSecond(preferences.getInt(IPreferenceConstants.P_MAX_REQUESTS_PER_SECOND));
		config.setMaxDescendants(preferences.getInt(IPreferenceConstants.P_MAX_SCAN_DESCENDANTS));
		config.setMaxChildren(preferences.getInt(IPreferenceConstants.P_MAX_SCAN_CHILDREN));
		config.setMaxDepth(preferences.getInt(IPreferenceConstants.P_MAX_SCAN_DEPTH));
		config.setMaxDuplicatePaths(preferences.getInt(IPreferenceConstants.P_MAX_SCAN_DUPLICATE_PATHS));
		config.setMaxResponseKilobytes(preferences.getInt(IPreferenceConstants.P_MAX_RESPONSE_LENGTH));

		final Thread probeThread = new Thread(new ScanProbeTask(shell, targetUri, scan));
		probeThread.start();

		return wizard.getTargetField();
	}

	// gross hack
	private List<Cookie> getCookieList(List<String> cookieStringList, URI uri) {
		if (cookieStringList.size() != 0) {
			ArrayList<Cookie> cookieList = new ArrayList<Cookie>(cookieStringList.size());
			for (String cookieString: cookieStringList) {
				List<HttpCookie> parseList = HttpCookie.parse(cookieString);
				for (HttpCookie cookie: parseList) {
					BasicClientCookie cp = new BasicClientCookie(cookie.getName(), cookie.getValue());
					cp.setComment(cookie.getComment());
					if (cookie.getDomain() != null) {
						cp.setDomain(cookie.getDomain());
					} else {
						// just set it to the target host for now - may need something slightly less specific
						cp.setDomain(uri.getHost());
					}
					long maxAge = cookie.getMaxAge();
					if (maxAge > 0) {
						Calendar calendar = Calendar.getInstance();
						calendar.add(Calendar.SECOND, (int) maxAge);
						cp.setExpiryDate(calendar.getTime());
					}
					cp.setPath(cookie.getPath());
					cp.setSecure(cookie.getSecure());
					cp.setVersion(cookie.getVersion());
					cookieList.add(cp);
				}
			}
			return cookieList;
		}
		return null;
	}
	
	private void runDomTest() {
		IScanner scanner = Activator.getDefault().getScanner();
		if(scanner != null) {
			scanner.runDomTests();
		}
	}
}
