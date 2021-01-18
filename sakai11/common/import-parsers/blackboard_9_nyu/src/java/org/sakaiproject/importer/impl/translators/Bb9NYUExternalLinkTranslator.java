package org.sakaiproject.importer.impl.translators;

import java.io.UnsupportedEncodingException;

import org.sakaiproject.importer.api.Importable;
import org.sakaiproject.importer.api.IMSResourceTranslator;
import org.sakaiproject.importer.impl.XPathHelper;
import org.sakaiproject.importer.impl.importables.WebLink;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class Bb9NYUExternalLinkTranslator implements IMSResourceTranslator {

	public String getTypeName() {
		return "resource/x-bb-externallink";
	}

	public Importable translate(Node resourceNode, Document descriptor, String contextPath, String archiveBasePath) {
		String url = XPathHelper.getNodeValue("/CONTENT/URL/@value", descriptor);
		String title = XPathHelper.getNodeValue("/CONTENT/TITLE/@value", descriptor).replaceAll("/", "_");
		try {
			contextPath = contextPath + java.net.URLEncoder.encode(url, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
		String description = XPathHelper.getNodeValue("/CONTENT/BODY/TEXT", descriptor);
		int priority = Integer.parseInt(((Element)resourceNode).getAttribute("priority"));
		WebLink link = new WebLink();
		link.setUrl(url);
		link.setTitle(title);
		link.setSequenceNum(priority);
		link.setContextPath(contextPath);
		link.setDescription(stripHTML(description));
		link.setAbsolute(url.indexOf("://") > -1);
		return link;
	}

	public boolean processResourceChildren() {
		return true;
	}


	private String stripHTML(String content) {
		// clean up the HTML
		content = content.replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("(\r\n|\n)", "<br />").replaceAll("&#xd;", "<br />");
		org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(content);
		
		// find the links
		org.jsoup.select.Elements links = doc.select("a[href]");
		
		// get the plain text of the HMTL from JSoup
		String cleanText = new Bb9NYUSmartTextDocumentTranslator().getPlainText(doc).trim();

		// replace empty old tags
		return cleanText.replaceAll("<>", "");
	}
		
}
