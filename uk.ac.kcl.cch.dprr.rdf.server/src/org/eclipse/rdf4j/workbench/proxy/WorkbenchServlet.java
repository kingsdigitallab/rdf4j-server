/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.workbench.proxy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.rdf4j.http.protocol.UnauthorizedException;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryManager;
import org.eclipse.rdf4j.workbench.base.AbstractServlet;
import org.eclipse.rdf4j.workbench.exceptions.BadRequestException;
import org.eclipse.rdf4j.workbench.exceptions.MissingInitParameterException;
import org.eclipse.rdf4j.workbench.util.BasicServletConfig;
import org.eclipse.rdf4j.workbench.util.DynamicHttpRequest;
import org.eclipse.rdf4j.workbench.util.TupleResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.kcl.ddh.jb.rdf.server.SharedRepositoryHandler;

public class WorkbenchServlet extends AbstractServlet {
	/*
     * This version of this class has been slightly modified by JB/DDH/KCL to make use of the shared
     * repository manager held by uk.ac.kcl.ddh.jb.rdf.server.SharedRepositoryHandler. The only change
     * is in the init(ServletConfig) method.   ... jb June 2017
	 */

	private static final Logger LOGGER = LoggerFactory.getLogger(WorkbenchServlet.class);

	private static final String DEFAULT_PATH = "default-path";

	private static final String NO_REPOSITORY = "no-repository-id";

	public static final String SERVER_PARAM = "server";

	private RepositoryManager manager;

	private final ConcurrentMap<String, ProxyRepositoryServlet> repositories = new ConcurrentHashMap<String, ProxyRepositoryServlet>();

	@Override
	public void init(final ServletConfig config)
		throws ServletException
	{
		SharedRepositoryHandler.initInstance(config);
		this.config = config;
		//if (config.getInitParameter(DEFAULT_PATH) == null) {
		//	throw new MissingInitParameterException(DEFAULT_PATH);
		//}
		if(SharedRepositoryHandler.getDefaultPath() == null) {
			throw new MissingInitParameterException(DEFAULT_PATH);
		}
		//final String param = config.getInitParameter(SERVER_PARAM);
		final String param = SharedRepositoryHandler.getServerURL();
		if (param == null || param.trim().isEmpty()) {
			throw new MissingInitParameterException(SERVER_PARAM);
		}
		try {
			// manager = createRepositoryManager(param);
			manager = SharedRepositoryHandler.getManager();
		}
		//catch (IOException e) {
		//	throw new ServletException(e);
		//}
		catch (RepositoryException e) {
			throw new ServletException(e);
		}
	}

	@Override
	public void destroy() {
		for (Servlet servlet : repositories.values()) {
			servlet.destroy();
		}
		manager.shutDown();
	}

	public void resetCache() {
		for (ProxyRepositoryServlet proxy : repositories.values()) {
			// inform browser that server changed and cache is invalid
			proxy.resetCache();
		}
	}

	@Override
	public void service(final HttpServletRequest req, final HttpServletResponse resp)
		throws ServletException, IOException
	{
		final String pathInfo = req.getPathInfo();
		if (pathInfo == null) {
			// final String defaultPath = config.getInitParameter(DEFAULT_PATH);   // replaced to use SharedRepositoryHandler   JB
			final String defaultPath = SharedRepositoryHandler.getDefaultPath();
			resp.sendRedirect(req.getRequestURI() + defaultPath);
		}
		else if ("/".equals(pathInfo)) {
			// final String defaultPath = config.getInitParameter(DEFAULT_PATH);   // replaced to use SharedRepositoryHandler   JB
			final String defaultPath = SharedRepositoryHandler.getDefaultPath();
			resp.sendRedirect(req.getRequestURI() + defaultPath.substring(1));
		}
		else if ('/' == pathInfo.charAt(0)) {
			try {
				handleRequest(req, resp, pathInfo);
			}
			catch (QueryResultHandlerException e) {
				throw new IOException(e);
			}
		}
		else {
			throw new BadRequestException("Request path must contain a repository ID");
		}
	}

	/**
	 * @param req
	 *        the servlet request
	 * @param resp
	 *        the servlet response
	 * @param pathInfo
	 *        the path info from the request
	 * @throws IOException
	 * @throws ServletException
	 * @throws QueryResultHandlerException
	 */
	private void handleRequest(final HttpServletRequest req, final HttpServletResponse resp,
			final String pathInfo)
		throws IOException, ServletException, QueryResultHandlerException
	{
		int idx = pathInfo.indexOf('/', 1);
		if (idx < 0) {
			idx = pathInfo.length();
		}
		final String repoID = pathInfo.substring(1, idx);
		try {
			service(repoID, req, resp);
		}
		catch (RepositoryConfigException e) {
			throw new ServletException(e);
		}
		catch (UnauthorizedException e) {
			handleUnauthorizedException(req, resp);
		}
		catch (ServletException e) {
			if (e.getCause() instanceof UnauthorizedException) {
				handleUnauthorizedException(req, resp);
			}
			else {
				throw e;
			}
		}
		catch (RepositoryException e) {
			throw new ServletException(e);
		}
	}

	/**
	 * @param req
	 * @param resp
	 * @throws IOException
	 * @throws QueryResultHandlerException
	 */
	private void handleUnauthorizedException(final HttpServletRequest req, final HttpServletResponse resp)
		throws IOException, QueryResultHandlerException
	{
		// Invalid credentials or insufficient authorization. Present
		// entry form again with error message.
		final TupleResultBuilder builder = getTupleResultBuilder(req, resp, resp.getOutputStream());
		builder.transform(this.getTransformationUrl(req), "server.xsl");
		builder.start("error-message");
		builder.result(
				"The entered credentials entered either failed to authenticate to the Sesame server, or were unauthorized for the requested operation.");
		builder.end();
	}

	private RepositoryManager createRepositoryManager(final String param)
		throws IOException, RepositoryException
	{
		RepositoryManager manager;
		if (param.startsWith("file:")) {
			manager = new LocalRepositoryManager(asLocalFile(new URL(param)));
		}
		else {
			manager = new RemoteRepositoryManager(param);
		}
		manager.initialize();
		return manager;
	}

	private File asLocalFile(final URL rdf)
		throws UnsupportedEncodingException
	{
		return new File(URLDecoder.decode(rdf.getFile(), "UTF-8"));
	}

	private void service(final String repoID, final HttpServletRequest req, final HttpServletResponse resp)
		throws RepositoryConfigException, RepositoryException, ServletException, IOException
	{
		LOGGER.info("Servicing repository: {}", repoID);
		setCredentials(req, resp);
		final DynamicHttpRequest http = new DynamicHttpRequest(req);
		final String path = req.getPathInfo();
		final int idx = path.indexOf(repoID) + repoID.length();
		http.setServletPath(http.getServletPath() + path.substring(0, idx));
		final String pathInfo = path.substring(idx);
		http.setPathInfo(pathInfo.length() == 0 ? null : pathInfo);
		if (repositories.containsKey(repoID)) {
			repositories.get(repoID).service(http, resp);
		}
		else {
			final Repository repository = manager.getRepository(repoID);
			if (repository == null) {
				final String noId = config.getInitParameter(NO_REPOSITORY);
				if (noId == null || !noId.equals(repoID)) {
					throw new BadRequestException("No such repository: " + repoID);
				}
			}
			final ProxyRepositoryServlet servlet = new ProxyRepositoryServlet();
			servlet.setRepositoryManager(manager);
			if (repository != null) {
				servlet.setRepositoryInfo(manager.getRepositoryInfo(repoID));
				servlet.setRepository(repository);
			}
			servlet.init(new BasicServletConfig(repoID, config));
			repositories.putIfAbsent(repoID, servlet);
			repositories.get(repoID).service(http, resp);
		}
	}

	private String getTransformationUrl(final HttpServletRequest req) {
		final String contextPath = req.getContextPath();
		return contextPath + config.getInitParameter(WorkbenchGateway.TRANSFORMATIONS);
	}

	/**
	 * Set the username and password for all requests to the repository.
	 * 
	 * @param req
	 *        the servlet request
	 * @param resp
	 *        the servlet response
	 * @throws MalformedURLException
	 *         if the repository location is malformed
	 */
	private void setCredentials(final HttpServletRequest req, final HttpServletResponse resp)
		throws MalformedURLException, RepositoryException
	{
		if (manager instanceof RemoteRepositoryManager) {
			final RemoteRepositoryManager rrm = (RemoteRepositoryManager)manager;
			LOGGER.info("RemoteRepositoryManager URL: {}", rrm.getLocation());
			final CookieHandler cookies = new CookieHandler(config);
			final String user = cookies.getCookieNullIfEmpty(req, resp, WorkbenchGateway.SERVER_USER);
			final String password = cookies.getCookieNullIfEmpty(req, resp, WorkbenchGateway.SERVER_PASSWORD);
			LOGGER.info("Setting user '{}' and password '{}'.", user, password);
			rrm.setUsernameAndPassword(user, password);
			// initialize() required to push credentials to internal HTTP
			// client.
			rrm.initialize();
		}
	}
}