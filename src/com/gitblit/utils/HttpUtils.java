/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.utils;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;

/**
 * Collection of utility methods for http requests.
 * 
 * @author James Moger
 * 
 */
public class HttpUtils {

	/**
	 * Returns the Gitblit URL based on the request.
	 * 
	 * @param request
	 * @return the host url
	 */
	public static String getGitblitURL(HttpServletRequest request) {
		// default to the request scheme and port
		String scheme = request.getScheme();
		int port = request.getServerPort();

		// try to use reverse-proxy server's port
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (StringUtils.isEmpty(forwardedPort)) {
        	forwardedPort = request.getHeader("X_Forwarded_Port");
        }
        if (!StringUtils.isEmpty(forwardedPort)) {
        	// reverse-proxy server has supplied the original port
        	try {
        		port = Integer.parseInt(forwardedPort);
        	} catch (Throwable t) {
        	}
        }
        
		// try to use reverse-proxy server's scheme
        String forwardedScheme = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.isEmpty(forwardedScheme)) {
        	forwardedScheme = request.getHeader("X_Forwarded_Proto");
        }
        if (!StringUtils.isEmpty(forwardedScheme)) {
        	// reverse-proxy server has supplied the original scheme
        	scheme = forwardedScheme;
        	
        	if ("https".equals(scheme) && port == 80) {
        		// proxy server is https, inside server is 80
        		// this is likely because the proxy server has not supplied
        		// x-forwarded-port. since 80 is almost definitely wrong,
        		// make an educated guess that 443 is correct.
        		port = 443;
        	}
        }
        
        String context = request.getContextPath();
        String forwardedContext = request.getHeader("X-Forwarded-Context");
        if (forwardedContext != null) {
        	forwardedContext = request.getHeader("X_Forwarded_Context");
        }
        if (!StringUtils.isEmpty(forwardedContext)) {
        	context = forwardedContext;
        }
        
        // trim any trailing slash
        if (context.length() > 0 && context.charAt(context.length() - 1) == '/') {
        	context = context.substring(1);
        }
        
		StringBuilder sb = new StringBuilder();
		sb.append(scheme);
		sb.append("://");
		sb.append(request.getServerName());
		if (("http".equals(scheme) && port != 80)
				|| ("https".equals(scheme) && port != 443)) {
			sb.append(":" + port);
		}
		sb.append(context);
		return sb.toString();
	}
	
	/**
	 * Returns a user model object built from attributes in the SSL certificate.
	 * This model is not retrieved from the user service.
	 *  
	 * @param httpRequest
	 * @param checkValidity ensure certificate can be used now
	 * @param usernameOIDs if unspecified, CN is used as the username
	 * @return a UserModel, if a valid certificate is in the request, null otherwise
	 */
	public static UserModel getUserModelFromCertificate(HttpServletRequest httpRequest, boolean checkValidity, String... usernameOIDs) {
		if (httpRequest.getAttribute("javax.servlet.request.X509Certificate") != null) {
			X509Certificate[] certChain = (X509Certificate[]) httpRequest
					.getAttribute("javax.servlet.request.X509Certificate");
			if (certChain != null) {
				X509Certificate cert = certChain[0];
				// ensure certificate is valid
				if (checkValidity) {
					try {
						cert.checkValidity(new Date());
					} catch (CertificateNotYetValidException e) {
						LoggerFactory.getLogger(HttpUtils.class).info(MessageFormat.format("X509 certificate {0} is not yet valid", cert.getSubjectDN().getName()));
						return null;
					} catch (CertificateExpiredException e) {
						LoggerFactory.getLogger(HttpUtils.class).info(MessageFormat.format("X509 certificate {0} has expired", cert.getSubjectDN().getName()));
						return null;
					}
				}
				return getUserModelFromCertificate(cert, usernameOIDs);
			}
		}
		return null;
	}
	
	/**
	 * Creates a UserModel from a certificate
	 * @param cert
	 * @param usernameOids if unspecified CN is used as the username
	 * @return
	 */
	public static UserModel getUserModelFromCertificate(X509Certificate cert, String... usernameOIDs) {
		UserModel user = new UserModel(null);
		user.isAuthenticated = false;
		
		// manually split DN into OID components
		// this is instead of parsing with LdapName which:
		// (1) I don't trust the order of values
		// (2) it filters out values like EMAILADDRESS
		String dn = cert.getSubjectDN().getName();
		Map<String, String> oids = new HashMap<String, String>();
		for (String kvp : dn.split(",")) {
			String [] val = kvp.trim().split("=");
			String oid = val[0].toUpperCase().trim();
			String data = val[1].trim();
			oids.put(oid, data);
		}
		
		if (usernameOIDs == null || usernameOIDs.length == 0) {
			// use default usename<->CN mapping
			usernameOIDs = new String [] { "CN" };
		}
		
		// determine username from OID fingerprint
		StringBuilder an = new StringBuilder();
		for (String oid : usernameOIDs) {
			String val = getOIDValue(oid.toUpperCase(), oids);
			if (val != null) {
				an.append(val).append(' ');
			}
		}
		user.username = an.toString().trim();
		
		// extract email address, if available
		user.emailAddress = getOIDValue("E", oids);
		if (user.emailAddress == null) {
			user.emailAddress = getOIDValue("EMAILADDRESS", oids);
		}		
		return user;
	}
	
	private static String getOIDValue(String oid, Map<String, String> oids) {
		if (oids.containsKey(oid)) {
			return oids.get(oid);
		}
		return null;
	}
}
