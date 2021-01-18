package org.sakaiproject.importer.impl.translators;

import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import org.sakaiproject.importer.api.IMSResourceTranslator;
import org.sakaiproject.importer.api.Importable;
import org.sakaiproject.importer.impl.XPathHelper;
import org.sakaiproject.importer.impl.importables.Folder;
import org.sakaiproject.importer.impl.importables.TextDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Bb9NYUSmartTextDocumentTranslator implements IMSResourceTranslator {

	public String getTypeName() {
		// Just in case it gets registered accidentally, we don't want it to list itself as a handler
		// for resource/x-bb-document - it would conflict with the the delegator that calls it - Bb9NYUDocumentTranslator
		return "resource/x-bb-document-smart-text";
	}

	public Importable translate(Node resourceNode, Document descriptor, String contextPath, String archiveBasePath) {
		String content = XPathHelper.getNodeValue("/CONTENT/BODY/TEXT", descriptor);
		String title = XPathHelper.getNodeValue("/CONTENT/TITLE/@value", descriptor).trim().replaceAll("\\<.*?\\>", "").replaceAll("/", "_");

                title = org.sakaiproject.importer.impl.TitleUniquifier.uniquify(title, resourceNode);

		int priority = Integer.parseInt(((Element)resourceNode).getAttribute("priority"));
		
		// clean up the HTML
		content = content.replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("(\r\n|\n)", "<br />").replaceAll("&#xd;", "<br />");
		org.jsoup.nodes.Document doc = Jsoup.parse(content);
		
		// find the links
		org.jsoup.select.Elements links = doc.select("a[href]");
		
		// get the plain text of the HMTL from JSoup
		String cleanText = getPlainText(doc).trim();

		// replace empty old tags
		cleanText = cleanText.replaceAll("<>", "");

		contextPath = contextPath + title; //Validator.escapeResourceName(title);

		//Some smart text has files, frequently zipped up 'learning modules'
		if (!XPathHelper.selectNode("/CONTENT/FILES", descriptor).hasChildNodes()) {
			TextDocument text = new TextDocument();
			text.setContent(cleanText);
			text.setTitle(title);
			text.setContextPath(contextPath);
			text.setSequenceNum(priority);
			return text;
		}
		else {
			Folder folder = new Folder();
			folder.setDescription(cleanText);
			folder.setTitle(title);
			folder.setPath(contextPath);
			folder.setSequenceNum(priority);

                        // BB9: the resource descriptor contains a <FILES>
                        // ... </FILES> block listing the files belonging to
                        // this resource, but there is no corresponding entry in
                        // imsmanifest.xml anymore.  Previously the importer
                        // would rely on the entry in imsmanifest.xml to tell it
                        // that files needed to be translated.
                        //
                        // To keep things working, we inject any files we find
                        // in the descriptor file back into the list for the
                        // resourceNode so they're processed as before.
                        //
                        if (XPathHelper.selectNode("./FILES", resourceNode) == null) {
                            Node copy = resourceNode.getOwnerDocument().importNode(XPathHelper.selectNode("/CONTENT/FILES", descriptor), true);

                            // Set the href to our filename relative to our parent.  This will later be used to derive the full path to this file.
                            NodeList fileNodes = copy.getChildNodes();
                            for (int i = 0; i < fileNodes.getLength(); i++) {
                                Node cloned = fileNodes.item(i).cloneNode(true);
                                ((Element)cloned).setAttribute("href", title + "/" + XPathHelper.getNodeValue("LINKNAME/@value", cloned));
                                resourceNode.appendChild(cloned);
                            }
                        }

			return folder;
		}
	}


	public boolean processResourceChildren() {
		return true;
	}
	
    public String getPlainText(org.jsoup.nodes.Element element) {
        FormattingVisitor formatter = new FormattingVisitor();
        NodeTraversor traversor = new NodeTraversor(formatter);
        traversor.traverse(element); // walk the DOM, and call .head() and .tail() for each node

        return formatter.toString();
    }
	
	private class FormattingVisitor implements NodeVisitor {
        private static final int maxWidth = 80;
        private int width = 0;
        private StringBuilder accum = new StringBuilder(); // holds the accumulated text
        
        private int linkCount = 1;
        private StringBuffer linkText = new StringBuffer();

        // hit when the node is first seen
        public void head(org.jsoup.nodes.Node node, int depth) {
            String name = node.nodeName();
            if (node instanceof TextNode) {
                append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
            }
            else if (name.equals("li")) {
                append("\n * ");
            }
        }

        // hit when all of the node's children (if any) have been visited
        public void tail(org.jsoup.nodes.Node node, int depth) {
            String name = node.nodeName();
            if (name.equals("br")) {
                append("\n");
            }
            else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5")) {
                append("\n\n");
            }
            else if (name.equals("a")) {
            	String tempHref = node.attr("href");
            	append(String.format(" [%s]", linkCount));
            	if (tempHref.contains("pk_string@X@")) {
            		linkText.append(String.format(" [%s] ", linkCount)).append(" [Blackboard Link Removed]").append("\n");
            	}
            	else {
            		//append(String.format(" [%s]", tempHref));
            		linkText.append(String.format(" [%s] ", linkCount)).append(tempHref).append("\n");
            	}
                
                // increment the linkCount
                linkCount++;
            }
        }

        // appends text to the string builder with a simple word wrap method
        private void append(String text) {
            if (text.startsWith("\n"))
                width = 0; // reset counter if starts with a newline. only from formats above, not in natural text
            if (text.equals(" ") &&
                    (accum.length() == 0 || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n")))
                return; // don't accumulate long runs of empty spaces

            if (text.length() + width > maxWidth) { // won't fit, needs to wrap
                String words[] = text.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    boolean last = i == words.length - 1;
                    if (!last) // insert a space if not the last word
                        word = word + " ";
                    if (word.length() + width > maxWidth) { // wrap and reset counter
                        accum.append("\n").append(word);
                        width = word.length();
                    } else {
                        accum.append(word);
                        width += word.length();
                    }
                }
            } else { // fits as is, without need to wrap text
                accum.append(text);
                width += text.length();
            }
        }
        
        public String toString() {
        	if (linkText != null && linkText.length() > 0) {
        		 return accum.toString() + "\n\n-------------------------\n" + linkText.toString();
        	}
            return accum.toString();
        }

	}


}
