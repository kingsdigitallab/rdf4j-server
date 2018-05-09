<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE xsl:stylesheet>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:sparql="http://www.w3.org/2005/sparql-results#" xmlns="http://www.w3.org/1999/xhtml">

	<xsl:output method="html" doctype-system="about:legacy-compat" />

	<xsl:variable name="info"
		select="document(sparql:sparql/sparql:head/sparql:link[@href='info']/@href)" />

	<xsl:template match="/">
		<html xml:lang="en" lang="en">
			<head>
				<title>
					<xsl:value-of select="$workbench.title" />
					-
					<xsl:value-of select="$title" />
				</title>
				<meta name="DC.title" content="{$title}" />
				<base href="/rdf/repositories/dprr/" />
				<link title="Default" rel="stylesheet" type="text/css"
					href="../../styles/default/print.css" media="print" />
				<link title="Default" rel="stylesheet" type="text/css"
					href="../../styles/default/screen.css" media="screen" />
				<link title="Basic" rel="alternate stylesheet" type="text/css"
					href="../../styles/basic/all.css" media="all" />
				<link rel="stylesheet" type="text/css"
                    href="../../styles/yasqe.min.css" />
				<link rel="shortcut icon" href="../../favicon.ico" type="image/ico" />
				<link rel="icon" href="../../favicon.png" type="image/png" />
			</head>
			<body>
				<div id="header">
					<div id="contentheader">
					<table>
						<tr>
							<td><a href="http://www.romanrepublic.ac.uk">DPRR Home</a></td>
						</tr>
						<tr>
							<td><a href="/rdf/doc/index.html">DPRR RDF Documentation</a></td>
						</tr>
						<tr>
							<td><a href="../../ontology/doc">DPRR Ontology Documentation</a></td>
						</tr>
					</table>
				</div>
					<div id="logo">
						
						<h2 class="mainHeader"><img src="../../images/logo.png" alt="dprr" /> Digital Prosopography of the Roman Republic</h2>
						<!-- img class="product" src="../../images/product.png" alt="workbench" / -->
					</div>
				</div>
				<div id="navigation">
					<ul class="maingroup">
						<xsl:call-template name="navigation" />
					</ul>
				</div>
				<div id="content">
					<h1 id="title_heading">
						<xsl:value-of select="$title" />
					</h1>
					<p id="noscript-message" class="ERROR">Scripting is not enabled. The
						OpenRDF Sesame Workbench
						application requires scripting to be
						enabled in order to work
						properly.
					</p>
					<!-- These scripts need to be loaded before other templates are applied. -->
					<script src="../../scripts/template.js" type="text/javascript"></script>
					<script src="../../scripts/jquery-1.11.0.min.js" type="text/javascript"></script>
					<xsl:apply-templates />
				</div>
				<div id="footer">
						<div>
							<xsl:value-of select="$copyright.label" />
						</div>
				</div>
			</body>
		</html>
	</xsl:template>

	<xsl:template name="navigation">
		<li>
			<a href=".">DPRR RDF Server</a>
		</li>
		<li>
			<xsl:value-of select="$explore.label" />
			<ul class="group">
				<xsl:call-template name="navigation-explore" />
			</ul>
		</li>
	</xsl:template>

	<xsl:template name="navigation-explore">
		<!-- Sometimes $info is not present. -->
		<xsl:variable name="enabled"
			select="$info//sparql:binding[@name='readable']/sparql:literal/text() = 'true'" />
		<xsl:variable name="disabled" select="not($enabled)" />
		<xsl:call-template name="navigation-entry">
			<xsl:with-param name="label" select="$namespaces.label" />
			<xsl:with-param name="href" select="'namespaces'" />
			<xsl:with-param name="disabled" select="$disabled" />
		</xsl:call-template>
		<xsl:call-template name="navigation-entry">
			<xsl:with-param name="label" select="$types.label" />
			<xsl:with-param name="href" select="'types'" />
			<xsl:with-param name="disabled" select="$disabled" />
		</xsl:call-template>
		<xsl:call-template name="navigation-entry">
			<xsl:with-param name="label" select="$explore.label" />
			<xsl:with-param name="href" select="'explore'" />
			<xsl:with-param name="disabled" select="$disabled" />
		</xsl:call-template>
		<xsl:call-template name="navigation-entry">
			<xsl:with-param name="label" select="$query.label" />
			<xsl:with-param name="href" select="'query'" />
			<xsl:with-param name="disabled" select="$disabled" />
		</xsl:call-template>
		<xsl:call-template name="navigation-entry">
			<xsl:with-param name="label" select="$export.label" />
			<xsl:with-param name="href" select="'export'" />
			<xsl:with-param name="disabled" select="$disabled" />
		</xsl:call-template>
	</xsl:template>

	<xsl:template name="navigation-entry">
		<xsl:param name="label" />
		<xsl:param name="href" />
		<xsl:param name="disabled" />
		<li>
			<xsl:choose>
				<xsl:when test="$disabled">
					<span class="disabled">
						<xsl:value-of select="$label" />
					</span>
				</xsl:when>
				<xsl:otherwise>
					<a href="{$href}">
						<xsl:value-of select="$label" />
					</a>
				</xsl:otherwise>
			</xsl:choose>
		</li>
	</xsl:template>

	<xsl:template name="limit-select">
		<xsl:param name="onchange" />
        <xsl:param name="limit_id" />
		<select>
            <xsl:attribute name="id">
                <xsl:value-of select="$limit_id" />
            </xsl:attribute>
            <xsl:attribute name="name">
                <xsl:value-of select="$limit_id" />
            </xsl:attribute>
			<xsl:if test="$onchange">
				<xsl:attribute name="onchange">
					<xsl:value-of select="$onchange" />
				</xsl:attribute>
			</xsl:if>
			<xsl:variable name="limit"
				select="$info//sparql:binding[@name='default-limit']/sparql:literal/text()" />
			<option value="0">
				<xsl:if test="$limit = '0'">
					<xsl:attribute name="selected">selected</xsl:attribute>
				</xsl:if>
				<xsl:value-of select="$all.label" />
			</option>
			<option value="10">
				<xsl:if test="$limit = '10'">
					<xsl:attribute name="selected">selected</xsl:attribute>
				</xsl:if>
				<xsl:value-of select="$limit10.label" />
			</option>
			<option value="50">
				<xsl:if test="$limit = '50'">
					<xsl:attribute name="selected">selected</xsl:attribute>
				</xsl:if>
				<xsl:value-of select="$limit50.label" />
			</option>
			<option value="100">
				<xsl:if test="$limit = '100'">
					<xsl:attribute name="selected">selected</xsl:attribute>
				</xsl:if>
				<xsl:value-of select="$limit100.label" />
			</option>
			<option value="200">
				<xsl:if test="$limit = '200'">
					<xsl:attribute name="selected">selected</xsl:attribute>
				</xsl:if>
				<xsl:value-of select="$limit200.label" />
			</option>
		</select>
	</xsl:template>

</xsl:stylesheet>
