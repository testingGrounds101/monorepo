package org.sakaiproject.importer.impl;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.sakaiproject.archive.api.ImportMetadata;
import org.sakaiproject.importer.api.IMSResourceTranslator;
import org.sakaiproject.importer.api.Importable;
import org.sakaiproject.importer.api.ImportFileParser;
import org.sakaiproject.importer.impl.importables.FileResource;
import org.sakaiproject.importer.impl.importables.Folder;
import org.sakaiproject.importer.impl.importables.HtmlDocument;
import org.sakaiproject.importer.impl.translators.Bb9NYUAnnouncementTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUAssessmentAttemptFilesTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUAssessmentAttemptTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUCollabSessionTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUCourseMembershipTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUCourseUploadsTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUDiscussionBoardTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUExternalLinkTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUGroupUploadsTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUHTMLDocumentTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUQuestionPoolTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUSurveyTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUTextDocumentTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUSmartTextDocumentTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUStaffInfoTranslator;
import org.sakaiproject.importer.impl.translators.Bb9NYUAssessmentTranslator;
import org.sakaiproject.util.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;

public class Blackboard9NYUFileParser extends IMSFileParser {
	
	public static final String BB_NAMESPACE_URI = "http://www.blackboard.com/content-packaging/";
    public static final String WEBCT_NAMESPACE_URI = "http://www.webct.com/IMS";
	
	public static final String ASSESSMENT_GROUP = "Assessments";
	public static final String ANNOUNCEMENT_GROUP = "Announcements";
	
	public static final String ASSESSMENT_FILES_DIRECTORY = "TQimages";
	
	public Blackboard9NYUFileParser() {
		// eventually, this will be spring-injected, 
		// but it's ok to hard-code this for now
		addResourceTranslator(new Bb9NYUAnnouncementTranslator());
		addResourceTranslator(new Bb9NYUAssessmentTranslator());
		addResourceTranslator(new Bb9NYUQuestionPoolTranslator());
		addResourceTranslator(new Bb9NYUSurveyTranslator());
		addResourceTranslator(new Bb9NYUAssessmentAttemptTranslator());
		addResourceTranslator(new Bb9NYUStaffInfoTranslator());
		addResourceTranslator(new Bb9NYUHTMLDocumentTranslator());
		addResourceTranslator(new Bb9NYUTextDocumentTranslator());
		addResourceTranslator(new Bb9NYUSmartTextDocumentTranslator());
		addResourceTranslator(new Bb9NYUExternalLinkTranslator());
		addResourceTranslator(new Bb9NYUCollabSessionTranslator());
		addResourceTranslator(new Bb9NYUAssessmentAttemptFilesTranslator());
		addResourceTranslator(new Bb9NYUCourseUploadsTranslator());
		addResourceTranslator(new Bb9NYUGroupUploadsTranslator());
		addResourceTranslator(new Bb9NYUCourseMembershipTranslator());
		addResourceTranslator(new Bb9NYUDiscussionBoardTranslator());
		resourceHelper = new Bb9NYUResourceHelper();
		itemHelper = new Bb9NYUItemHelper();
		fileHelper = new Bb9NYUFileHelper();
		manifestHelper = new Bb9NYUManifestHelper();
	}
	
	public ImportFileParser newParser() {
		return new Blackboard9NYUFileParser();
	}
	
	public boolean isValidArchive(byte[] fileData) {
		if (super.isValidArchive(new ByteArrayInputStream(fileData))) {
			Document manifest = extractFileAsDOM("/imsmanifest.xml", new ByteArrayInputStream(fileData));

                        if (!hasBb9Version(fileData)) {
                            return false;
                        }


                        if (enclosingDocumentContainsNamespaceDeclaration(manifest, BB_NAMESPACE_URI)) { 
                            return true;
                        } else if (enclosingDocumentContainsNamespaceDeclaration(manifest, WEBCT_NAMESPACE_URI)) {
                            return true;
                        } else {
                            return false;
                        }
		} else return false;
	}


	private String readFullInputStream(InputStream stream) throws IOException {
		InputStreamReader reader = new InputStreamReader(stream);
		char[] buffer = new char[4096];

		StringBuilder sb = new StringBuilder();
		int len;
		while ((len = reader.read(buffer, 0, buffer.length)) >= 0) {
			sb.append(buffer, 0, len);
		}

		return sb.toString();
	}

	private boolean hasBb9Version(byte[] fileData) {
		ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(fileData));

		try {
			while (true) {
				ZipEntry entry = (ZipEntry) zipStream.getNextEntry();

				if (entry == null) {
					return false;
				}

				if (".bb-package-info".equals(entry.getName())) {
					String packageInfo = readFullInputStream(zipStream);
					return (packageInfo.indexOf("app.release.number=9.") >= 0);
				}
			}
		} catch (IOException e) {
			return false;
		}
	}


	private boolean enclosingDocumentContainsNamespaceDeclaration(Node node, String nameSpaceURI) {
		return node.lookupPrefix(nameSpaceURI) != null;
	}

	protected boolean isCompoundDocument(Node node, Document resourceDescriptor) {
		// the rule we're observing is that any document of type resource/x-bb-document
		// that has more than one child will be treated as a compound document
		return "resource/x-bb-document".equals(XPathHelper.getNodeValue("./@type",node)) &&
	       node.hasChildNodes() && (node.getChildNodes().getLength() > 1);
	}
	
	protected Importable getCompanionForCompoundDocument(Document resourceDescriptor, Folder folder) {
		HtmlDocument html = new HtmlDocument();
		StringBuffer content = new StringBuffer();
		
		String bbText = XPathHelper.getNodeValue("/CONTENT/BODY/TEXT", resourceDescriptor);
		bbText = StringUtils.replace(bbText, "@X@EmbeddedFile.location@X@", "");
		content.append("    <p>" + bbText + "</p>\n");

		List<Node> fileNodes = XPathHelper.selectNodes("/CONTENT/FILES/FILE", resourceDescriptor);
		content.append("    <table class=\"bb-files\"><tr><th colspan=\"2\">Included Files</th></tr>\n");
		int cnt = 1;
		for (Node fileNode : fileNodes) {
			String fileName = XPathHelper.getNodeValue("./NAME", fileNode);
			content.append("<tr><td>" + cnt + ") </td><td><a href=\"" + fileName + "\">" + fileName + "</a></td></tr>\n");
			cnt++;
		}
		content.append("    </table>\n");
		html.setContent(content.toString());
		html.setTitle(folder.getTitle());
		html.setContextPath(folder.getPath() + folder.getTitle().replaceAll("/", "_") + "/introduction");
		html.setLegacyGroup(folder.getLegacyGroup());
		// we want the html document to come before the folder in sequence
		html.setSequenceNum(folder.getSequenceNum() - 1);
		return html;
	}

	protected boolean wantsCompanionForCompoundDocument() {
		return true;
	}
	
	protected Collection getCategoriesFromArchive(String pathToData) {
		Collection categories = new ArrayList();
		Collection angelCategories = new ArrayList();
		ImportMetadata im;
		ImportMetadata aim;
		Node topLevelItem;
		String resourceId;
		Node resourceNode;
		String targetType;
		List topLevelItems = manifestHelper.getTopLevelItemNodes(this.archiveManifest);
		for(Iterator i = topLevelItems.iterator(); i.hasNext(); ) {
			topLevelItem = (Node)i.next();
			
			// save FOR ANGEL
			aim = new BasicImportMetadata();
			aim.setId(itemHelper.getId(topLevelItem));
			aim.setLegacyTool(itemHelper.getTitle(topLevelItem));
			aim.setMandatory(false);
			aim.setFileName(".xml");
			aim.setSakaiServiceName("ContentHostingService");
			aim.setSakaiTool("Resources");
			angelCategories.add(aim);

			// Each course TOC item has a target type.
			// At present, we only handle the CONTENT 
			// and STAFF_INFO target types,
			// with assessments and announcements being identified
			// separately below.
			resourceId = XPathHelper.getNodeValue("./@identifierref", topLevelItem);
			resourceNode = manifestHelper.getResourceForId(resourceId, this.archiveManifest);
			targetType = XPathHelper.getNodeValue("/COURSETOC/TARGETTYPE/@value", resourceHelper.getDescriptor(resourceNode));
			if (!(("CONTENT".equals(targetType)) || ("STAFF_INFO").equals(targetType))) continue;
			
			im = new BasicImportMetadata();
			im.setId(itemHelper.getId(topLevelItem));
			im.setLegacyTool(itemHelper.getTitle(topLevelItem));
			im.setMandatory(false);
			im.setFileName(".xml");
			im.setSakaiServiceName("ContentHostingService");
			im.setSakaiTool("Resources");
			categories.add(im);
		}
		// Figure out if there are assessments 
		if (XPathHelper.selectNodes("//resource[@type='assessment/x-bb-qti-test']", this.archiveManifest).size() 
				+ XPathHelper.selectNodes("//resource[@type='assessment/x-bb-qti-pool']", this.archiveManifest).size()
				+ XPathHelper.selectNodes("//resource[@type='assessment/x-bb-qti-survey']", this.archiveManifest).size() > 0) {
			im = new BasicImportMetadata();
			im.setId("assessments");
	                im.setLegacyTool(ASSESSMENT_GROUP);
	                im.setMandatory(false);
	                im.setFileName(".xml");
	                im.setSakaiTool("Tests & Quizzes");
	                categories.add(im);
		}
		
		// Figure out if we need an Announcements category
		if (XPathHelper.selectNodes("//resource[@type='resource/x-bb-announcement']", this.archiveManifest).size() > 0) {
			im = new BasicImportMetadata();
			im.setId("announcements");
	                im.setLegacyTool(ANNOUNCEMENT_GROUP);
	                im.setMandatory(false);
	                im.setFileName(".xml");
	                im.setSakaiTool("Announcements");
	                categories.add(im);
		}
		if (categories.size() == 0) {
			return angelCategories;
		}
		return categories;
	}
	
	protected Collection<Object> translateFromNodeToImportables(Node node, String contextPath, int priority, Importable parent) {
		Collection<Object> branchOfImportables = new ArrayList<Object>();
		String tag = node.getNodeName();
		String itemResourceId = null;
		if ("item".equalsIgnoreCase(tag)) {
			itemResourceId = itemHelper.getResourceId(node);
		} else if ("resource".equalsIgnoreCase(tag)) {
			itemResourceId = resourceHelper.getId(node);
		} else if ("file".equalsIgnoreCase(tag)) {
			itemResourceId = resourceHelper.getId(node.getParentNode());
		}
		Document resourceDescriptor = resourceHelper.getDescriptor(manifestHelper.getResourceForId(itemResourceId, this.archiveManifest));
		if (resourceHelper.isFolder(resourceDescriptor) || 
	  		    ("item".equalsIgnoreCase(tag) && (XPathHelper.selectNodes("./item", node).size() > 0)) ||
	  		    ( "item".equalsIgnoreCase(tag) && 
	  	          isCompoundDocument(manifestHelper.getResourceForId(itemResourceId, archiveManifest),resourceDescriptor)
	  		    )) {
			String folderTitle = getTitleForNode(node);
			// NYU-7 strip tags from folder name
			folderTitle = folderTitle.trim().replaceAll("\\<.*?\\>", "").replaceAll("/", "_");

			Folder folder = new Folder();
			folder.setPath(contextPath);
			folder.setTitle(folderTitle);
			folder.setDescription(getDescriptionForNode(node));
			folder.setSequenceNum(priority);
			if (parent != null) {
  				folder.setParent(parent);
  				folder.setLegacyGroup(parent.getLegacyGroup());
  			} else folder.setLegacyGroup(folderTitle);

                        // BB9 has a bunch of folders called --TOP-- that we're actually not interested in.
                        //
                        // If we see one of those, process its children but set their parent to the --TOP-- node's
                        // parent (cutting --TOP-- out of the hierarchy)
                        Importable childrenParent = folder;

			// now we take care of the folder's child Nodes
			// construct a new path and make sure we replace any forward slashes from the resource title
			String folderPath = contextPath + folderTitle.replaceAll("/", "_") + "/";

                        if ("--TOP--".equals(folderTitle)) {
                            childrenParent = parent;
                            folderPath = contextPath;
                        }

			if (isCompoundDocument(manifestHelper.getResourceForId(itemResourceId, archiveManifest),resourceDescriptor)) {
				if (wantsCompanionForCompoundDocument()) {
					priority++;
					folder.setSequenceNum(priority);
					branchOfImportables.add(getCompanionForCompoundDocument(resourceDescriptor, folder));
				}
                                Node nextNode = manifestHelper.getResourceForId(itemResourceId, archiveManifest);

                                if (nextNode != node) {
                                  branchOfImportables.addAll(translateFromNodeToImportables(nextNode, folderPath, priority, childrenParent));
                                } else {
                                  System.err.println("WARNING: loop prevented for path: " + folderPath + " itemResourceId: " + itemResourceId);
                                }
			} else {
	  			List<Node> children = XPathHelper.selectNodes("./item", node);
	  			int childPriority = 1;
	  			for (Iterator<Node> i = children.iterator(); i.hasNext();) {
	  				branchOfImportables.addAll(
	  						translateFromNodeToImportables((Node)i.next(),folderPath, childPriority, childrenParent));
	  				childPriority++;
	  			}
			}
  			resourceMap.remove(itemResourceId);
                        if (!"--TOP--".equals(folderTitle)) {
                            branchOfImportables.add(folder);
                        }
		} // node is folder
		
		else if("item".equalsIgnoreCase(tag)) {
			// this item is a leaf, so we handle the resource associated with it
			Node resourceNode = manifestHelper.getResourceForId(itemResourceId, this.archiveManifest);
  			if (resourceNode != null) {
  				if (parent == null) {
  					parent = new Folder();
  					parent.setLegacyGroup(itemHelper.getTitle(node));
  				}
  				branchOfImportables.addAll(
  						translateFromNodeToImportables(resourceNode,contextPath, priority, parent));
  			}
		} else if("file".equalsIgnoreCase(tag)) {
			FileResource file = new FileResource();
			try {
				String fileName = fileHelper.getFilenameForNode(node).replaceAll("\\<.*?\\>", "");
 				file.setFileName(fileName);
 				
 				// this will get the bb:title (friendly title, not filename)
 				String folderName = fileHelper.getTitle(node);
 				
 				// first try to remove all special characters 
 				folderName = Normalizer.normalize(folderName, Normalizer.Form.NFD);
 				folderName = folderName.replaceAll("[^\\p{ASCII}]", "");

 				// second try to split the folderName with a comma
 				if (StringUtils.contains(folderName, ",")) {
 					String[] splitName = StringUtils.split(folderName, ",");
 					folderName = StringUtils.left(splitName[0], 24);
 				}
 				
 				// custom vars to help put this single file into a subfolder 
 				//String fileHolderFolder = fileName.substring(0, fileName.lastIndexOf('.'));
 				boolean putThisFileIntoAFolder = false;
 				 
				if (node.getParentNode().getChildNodes().getLength() > 1) {
					file.setDescription("");
				} else {
					// BB9: Seems like we don't want an intro file at all
 					//
					// // Duke: stay consistent and null it
					// file.setDescription("");
					// putThisFileIntoAFolder = true;
					// String newItemText = resourceHelper.getDescription(node.getParentNode());
					// newItemText = StringUtils.replace(newItemText, "@X@EmbeddedFile.location@X@", "");
					
					// if (StringUtils.trimToNull(newItemText) != null) {
					// 	HtmlDocument introFile = new HtmlDocument();
					// 	String tmpName = contextPath + folderName + "/" + folderName;
					// 	introFile.setContextPath(tmpName);
					// 	introFile.setTitle(folderName);
					// 	introFile.setContent(newItemText);
					// 	if (parent != null) {
					// 		introFile.setParent(parent);
					// 		introFile.setLegacyGroup(parent.getLegacyGroup());
					// 	}
					// 	branchOfImportables.add(introFile);
					// }
				}
				file.setInputStream(new ByteArrayInputStream(fileHelper.getFileBytesForNode(node, contextPath)));				

				if (putThisFileIntoAFolder && !((Bb9NYUFileHelper)fileHelper).isPartOfAssessment(node)) {
					file.setDestinationResourcePath(contextPath + folderName + "/" + fileName);
				}
				else {
					file.setDestinationResourcePath(
						fileHelper.getFilePathForNode(node, contextPath).
							replaceAll("//", "/").replaceAll("embedded/", "") /*.replace(fileHolderFolder, fileHolderFolder + "/" + fileHolderFolder) */
						);
				}

				file.setContentType(this.mimeTypes.getContentType(fileName));
				file.setTitle(fileHelper.getTitle(node));
				if(parent != null) {
					file.setParent(parent);
					file.setLegacyGroup(parent.getLegacyGroup());
				} else file.setLegacyGroup("");
			} catch (IOException e) {
				resourceMap.remove(resourceHelper.getId(node.getParentNode()));
				return branchOfImportables;
			}
			branchOfImportables.add(file);
			resourceMap.remove(resourceHelper.getId(node.getParentNode()));
			return branchOfImportables;
		} else if("resource".equalsIgnoreCase(tag)) {
			// TODO handle a resource node
			Importable resource = null;
			boolean processResourceChildren = true;
			IMSResourceTranslator translator = (IMSResourceTranslator)translatorMap.get(resourceHelper.getType(node));
			if (translator != null) {
				String title = resourceHelper.getTitle(node);
				((Element)node).setAttribute("title", title);
				((Element)node).setAttribute("priority", Integer.toString(priority));
				resource = translator.translate(node, resourceHelper.getDescriptor(node), contextPath, this.pathToData);
				processResourceChildren = translator.processResourceChildren();
			}
			if (resource != null) {
				// make a note of a dependency if there is one.
				String dependency = resourceHelper.getDependency(node);
				if (!"".equals(dependency)) {
					dependencies.put(resourceHelper.getId(node), dependency);
				}
				// section to twiddle with the Importable's legacyGroup,
				// which we only want to do if it hasn't already been set.
				if ((resource.getLegacyGroup() == null) || ("".equals(resource.getLegacyGroup()))) {
					// find out if something depends on this.
					if (dependencies.containsValue(resourceHelper.getId(node))) {
						resource.setLegacyGroup("mandatory");
					} else if (parent != null) {
						resource.setParent(parent);
						resource.setLegacyGroup(parent.getLegacyGroup());
					} else resource.setLegacyGroup(resourceHelper.getTitle(node));
				}
				branchOfImportables.add(resource);
				parent = resource;
			}
			// processing the child nodes implies that their files can wind up in the Resources tool.
			// this is not always desirable, such as the QTI files from assessments.
			if (processResourceChildren) {
				NodeList children = node.getChildNodes();
		  		for (int i = 0;i < children.getLength();i++) {
                                    branchOfImportables.addAll(translateFromNodeToImportables(children.item(i), contextPath, priority, parent));
                                }
			}
			resourceMap.remove(itemResourceId);
		}

		return branchOfImportables;
	}


	protected class Bb9NYUResourceHelper extends ResourceHelper {
		public String getTitle(Node resourceNode) {
			String title = resourceNode.getAttributes().getNamedItem("bb:title").getNodeValue().trim().replaceAll("\\<.*?\\>", "").replaceAll("/", "_");
			return TitleUniquifier.uniquify(title, resourceNode);
		}
		
		public String getType(Node resourceNode) {
			
			String nodeType = XPathHelper.getNodeValue("./@type", resourceNode);
			
			if ("resource/x-bb-document".equals(nodeType)) {
				/*
				 * Since we've gotten a bb-document, we need to figure out what kind it is. Known possible are:
				 *   1. x-bb-externallink
				 *   2. x-bb-document
				 *     a. Plain text
				 *     b. Smart text
				 *     c. HTML
				 * The reason we have to do this is that all the above types are listed as type "resource/x-bb-document"
				 *  in the top level resource node. Their true nature is found with the XML descriptor (.dat file) 
				 */
				
				if(resourceNode.hasChildNodes()) {
					// If it has child-nodes (files, usually) we don't want to parse the actual document
					return nodeType;
				}
				
				String subType = XPathHelper.getNodeValue("/CONTENT/CONTENTHANDLER/@value", resourceHelper.getDescriptor(resourceNode));	
				if ("resource/x-bb-externallink".equals(subType)) {
					nodeType = "resource/x-bb-externallink";
				} else if ("resource/x-bb-asmt-test-link".equals(subType)) {
					nodeType = "resource/x-bb-asmt-test-link";
				} else {
					String docType = XPathHelper.getNodeValue("/CONTENT/BODY/TYPE/@value", resourceHelper.getDescriptor(resourceNode));
					if ("H".equals(docType)) {
						// NYU-2
						nodeType = "resource/x-bb-document-smart-text";
					} else if ("P".equals(docType)) {
						nodeType = "resource/x-bb-document-plain-text";
					} else if ("S".equals(docType)) {
						nodeType = "resource/x-bb-document-smart-text";
					}
				}
			}
			
			return nodeType;
		}
		
		public String getId(Node resourceNode) {
			return XPathHelper.getNodeValue("./@identifier", resourceNode);
		}
		
		public Document getDescriptor(Node resourceNode) {
			try {
				String descriptorFilename = resourceNode.getAttributes().getNamedItem("bb:file").getNodeValue();
				DocumentBuilder docBuilder;
				docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			    InputStream fis = new FileInputStream(pathToData + "/" + descriptorFilename);
			    return (Document) docBuilder.parse(fis);
			} catch (Exception e) {
				return null;
			}
		}

		public String getDescription(Node resourceNode) {
			Document descriptor = resourceHelper.getDescriptor(resourceNode);
			return XPathHelper.getNodeValue("/CONTENT/BODY/TEXT", descriptor);
		}
		
		public boolean isFolder(Document resourceDescriptor) {
			return "true".equals(XPathHelper.getNodeValue("/CONTENT/FLAGS/ISFOLDER/@value", resourceDescriptor));
		}
	}
	

	protected class Bb9NYUItemHelper extends ItemHelper {

		public String getId(Node itemNode) {
			return XPathHelper.getNodeValue("./@identifier", itemNode);
		}

		private String getTitleHelper(Node itemNode) {
			String tempString = XPathHelper.getNodeValue("./title",itemNode);



            if (tempString.equals("content.Assignments.label")) return "Assignments";
            if (tempString.equals("content.Documents.label")) return "Documents";
            if (tempString.equals("content.Syllabus.label")) return "Syllabus";
            if (tempString.equals("staff_information.FacultyInformation.label")) return "Faculty Information";
            if (tempString.equals("COURSE_DEFAULT.Assignments.CONTENT_LINK.label")) return "Assignments";
            if (tempString.equals("COURSE_DEFAULT.Communication.APPLICATION.label")) return "Communication";
            if (tempString.equals("COURSE_DEFAULT.CourseInformation.CONTENT_LINK.label")) return "Course Information";
            if (tempString.equals("COURSE_DEFAULT.ExternalLinks.CONTENT_LINK.label")) return "External Links";
            if (tempString.equals("COURSE_DEFAULT.CourseDocuments.CONTENT_LINK.label")) return "Documents";
            if (tempString.equals("COURSE_DEFAULT.StaffInformation.STAFF.label")) return "Staff";
            if (tempString.equals("ORGANIZATION_DEFAULT.Information.CONTENT_LINK.label")) return "Information";
            if (tempString.equals("ORGANIZATION_DEFAULT.Documents.CONTENT_LINK.label")) return "Documents";
            if (tempString.equals("ORGANIZATION_DEFAULT.ExternalLinks.CONTENT_LINK.label")) return "External Links";
			return tempString;
		}

            
		// Wraps the original getTitle (now renamed to getTitleHelper) being careful to make sure titles are unique for the same parent.
		public String getTitle(Node itemNode) {
			String title = getTitleHelper(itemNode);
			return TitleUniquifier.uniquify(title, itemNode);
		}

		public String getDescription(Node itemNode) {
			String resourceId = XPathHelper.getNodeValue("./@identifierref", itemNode);
			Node resourceNode = manifestHelper.getResourceForId(resourceId, archiveManifest);
			return resourceHelper.getDescription(resourceNode);
		}
		
	}
	
	protected class Bb9NYUManifestHelper extends ManifestHelper {

		public List getItemNodes(Document manifest) {
			return XPathHelper.selectNodes("//item", manifest);
		}

		public Node getResourceForId(String resourceId, Document manifest) {
			return XPathHelper.selectNode("//resource[@identifier='" + resourceId + "']",archiveManifest);
		}

		public List getResourceNodes(Document manifest) {
			return XPathHelper.selectNodes("//resource", manifest);
		}

		public List getTopLevelItemNodes(Document manifest) {
			return XPathHelper.selectNodes("//organization/item", manifest);
		}

	}
	
	protected class Bb9NYUFileHelper extends FileHelper {
		
		private File findBB9File(String basePath, Node node) {
			String linkName = XPathHelper.getNodeValue("./LINKNAME/@value", node);
			final String name  = XPathHelper.getNodeValue("./NAME", node);

			int extensionPosition = linkName.lastIndexOf(".");

			if (extensionPosition < 0) {
				extensionPosition = linkName.length();
			}

			final String extension = linkName.substring(extensionPosition);

			File baseFile = new File(basePath);

			if (!baseFile.exists()) {
				System.out.println("File not found: " + basePath);
				return null;
			}

			Iterator<File> matches = FileUtils.iterateFiles(baseFile,
															new IOFileFilter() {
																public boolean accept(File file) {
																	return accept(file.getParentFile(), file.getName());
																}

																public boolean accept(File dir, String filename) {
																	// The filename will contain "name" (an ID) prefixed by
																	// underscores, followed by an optional extension.
																	//
																	// Each data file has a counterpart with the same filename
																	// plus an ".xml" extension, so we want to be careful not
																	// to pick up this file by mistake.
																	return (filename.matches(".*__" + name.substring(1) + "\\b.*") &&
																			!filename.toLowerCase().endsWith(".xml"));
																}
															},
															TrueFileFilter.INSTANCE);

			if (matches.hasNext()) {
				return matches.next();
			}

			System.out.println("No file found matching linkName: " + linkName + " with name: " + name);
			return null;
		}

		public byte[] getFileBytesForNode(Node node, String contextPath) throws IOException {
			//for Bb we ignore the contextPath...
			String basePath = XPathHelper.getNodeValue("./@identifier",node.getParentNode());
			String fileHref = XPathHelper.getNodeValue("./@href", node).replaceAll("\\\\", File.separator);
			String filePath = basePath + File.separator + fileHref;
			File f = new File(pathToData + File.separator + filePath);

			if (!f.exists()) { // if the file doesn't exist in the location
				filePath = fileHref;
				f = new File(pathToData + File.separator + filePath);	
			}
			
			if (f.exists() && !f.isDirectory()) {
					System.out.println("Found file directly: " + pathToData + File.separator + filePath + ";exists=" + f.exists());
					return getBytesFromFile(f);
			}
			
			// BB9: if this file is a "csfiles" entry, dig it out now.
			if ("CS".equals(XPathHelper.getNodeValue("./STORAGETYPE/@value", node))) {
				// f = new File(pathToData + File.separator +
				// 			 "csfiles" + File.separator +
				// 			 "home_dir" + File.separator +
				// 			 generateBB9Filename(node));

				f = findBB9File(pathToData + File.separator +
								"csfiles" + File.separator +
								"home_dir",
								node);

				if (f != null && f.exists()) {
					return getBytesFromFile(f);
				}
			}


			// now iterate through to explicitly find the file (bad exclamation mark file issue)	
			System.out.println("Folder: " + pathToData + File.separator + basePath + File.separator);
			File folder = new File(pathToData + File.separator + basePath + File.separator);
			File[] listOfFiles = folder.listFiles();

			if (listOfFiles == null) {
				System.err.println("Got a null directory for base path: " + basePath + " href: " + fileHref);
				System.err.println("Context path was: " + contextPath);
			}


			try {
				for (int i = 0; i < listOfFiles.length; i++) {
  					if (listOfFiles[i].isFile()) {
  						String fileName = listOfFiles[i].getName();
  						String thePath = getFileFromStrangePath(fileName, fileHref);

						System.out.println("Searching for strange path: " + thePath);

  						if (!StringUtils.isEmpty(thePath)) {
  	  						filePath = basePath + File.separator + thePath;
  	  						f = new File(pathToData + File.separator + filePath);
  	  							
  	  						if (f.exists()) {
  	  							return getBytesFromFile(f);
  	  						}
  						}
  						
						filePath = basePath + File.separator + fileName;
  					} 
  					else if (listOfFiles[i].isDirectory()) {
  						File[] subDir = listOfFiles[i].listFiles();
    					//System.out.println("Directory " + listOfFiles[i].getName());
    					
    					for (int j = 0; j < subDir.length; j++) {
    						if (subDir[j].isFile()) {
    	  						String thePath = getFileFromStrangePath(subDir[j].getName(), fileHref);
    	  						System.out.println("s3:" + listOfFiles[i].getName() + File.separator + subDir[j].getName());
    	  						
    	  						if (!StringUtils.isEmpty(thePath)) {
        	  						filePath = basePath + File.separator + listOfFiles[i].getName() + File.separator + thePath;
        	  						System.out.println("sub: " + filePath);
    	  	  						f = new File(pathToData + File.separator + filePath);
    	  	  					System.out.println("sub2: " + pathToData + File.separator + filePath);
    	  	  							
    	  	  						if (f.exists()) {
    	  	  							return getBytesFromFile(f);
    	  	  						}
    	  						}
    	  						
    							filePath = basePath + File.separator + listOfFiles[i].getName() + File.separator + subDir[j].getName();
    	  					}
    					}
  					}
 				}
			}
			catch (Exception exc) {
				exc.printStackTrace();
				System.out.println("Bad folder: " + exc.getMessage());
			}

			System.out.println("never found a matching file  so just returning last doc");
			return getBytesFromFile(new File(pathToData + File.separator + filePath));
		}	
	

		public boolean isPartOfAssessment(Node node) {
			String parentType = XPathHelper.getNodeValue("../@type", node);
			return ("assessment/x-bb-qti-pool".equals(parentType) || "assessment/x-bb-qti-test".equals(parentType));
		}
			

		public String getFilePathForNode(Node node, String contextPath) {
			// for files that are part of an assessment, we're going to
			// tack on an extra container folder to the path.
			if (isPartOfAssessment(node)) {
				contextPath = contextPath + "/" + ASSESSMENT_FILES_DIRECTORY;
			}
			String fileHref = XPathHelper.getNodeValue("./@href", node);
			fileHref = fileHref.replaceAll("\\\\", "/");

			String parentType = XPathHelper.getNodeValue("../@type", node);

			if (parentType.equals("webcontent")) {
				System.out.println("filehref: " + fileHref);
				if (fileHref.indexOf("/") > -1) {
					String [] temp = null;	
					temp = fileHref.split("/");

					String resourceId = XPathHelper.getNodeValue("./@identifier", node.getParentNode());
					System.out.println("resourceId: " + resourceId);
					Node itemNode = XPathHelper.selectNode("//item[@identifierref='" + resourceId + "']",archiveManifest);
					String tempString = XPathHelper.getNodeValue("./title",itemNode);
					tempString = tempString.replaceAll("\\<.*?\\>", ""); // get rid of HTML tags in a title
					System.out.println("getting title from item: " + tempString);

				
					System.out.println("temp2: " + temp[2] + ";length: " + temp[2].length());
					if (temp[2].length() == 38) {
					System.out.println("temp2: " + temp[2] + ";length: " + temp[2].length());
						return contextPath + "/" + tempString.replaceAll("\\\\", "/") + "/index.html";
					}
					else {
						return contextPath + "/" +tempString.replaceAll("\\\\", "/") + "/" + temp[2];
					}
				}		
			}
			return contextPath + "/" + fileHref.replaceAll("\\\\", "/");
		}

		public String getTitle(Node fileNode) {
/*
			String resourceId = XPathHelper.getNodeValue("./@identifier", fileNode.getParentNode());
			System.out.println("resourceId: " + resourceId);
			Node itemNode = XPathHelper.selectNode("//item[@identifierref='" + resourceId + "']",archiveManifest);
			String tempString = XPathHelper.getNodeValue("./title",itemNode);
			System.out.println("getting title from item: " + tempString);
*/

            // if the resource that this file belongs to has multiple files,
            // we just want to use the filename as the title
            if (fileNode.getParentNode().getChildNodes().getLength() > 1) {
                return getFilenameForNode(fileNode);
            } 
			else {
				return resourceHelper.getTitle(fileNode.getParentNode());
			}
        }

		public String getFilenameForNode(Node node) {
            String linkName = XPathHelper.getNodeValue("./LINKNAME/@value", node);

            if (linkName != null && !linkName.equals("")) {
                return linkName;
            }


            String sourceFilePath = XPathHelper.getNodeValue("./@href", node).replaceAll("\\\\", "/");
            String temp =  (sourceFilePath.lastIndexOf("/") < 0) ? sourceFilePath
                    : sourceFilePath.substring(sourceFilePath.lastIndexOf("/") + 1);

			if (temp.indexOf(".htm") > -1 && temp.length() == 38) {
				return "index.html";
			}
			else {
				return temp;
			}

        }
		
		private String getFileFromStrangePath(String fileName, String fileHref) {
			String strippedFileName = fileName;
			String strippedFileExtension = "";
			if (fileName.startsWith("!")) {
				strippedFileName = fileName.substring(1);
				
				int suffixPos = strippedFileName.lastIndexOf(".");
				strippedFileName = strippedFileName.substring(0, suffixPos);
				strippedFileExtension = fileName.substring(suffixPos+1, fileName.length());
				
				byte[] hexedName = null;
				try {
					hexedName = Hex.decodeHex(strippedFileName.toCharArray());
				} catch (DecoderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String hexString = new String(hexedName);
				//System.out.println("sfile: " + strippedFileName + ";extension=" + strippedFileExtension + ";hex=" + hexString);
				
				String fileNameWithoutExtension = fileHref;
				String fileNameExtension = "";
				
				// just get the filename not the whole path
				if (fileNameWithoutExtension.contains("/")) {
					fileNameWithoutExtension = fileNameWithoutExtension.substring(fileNameWithoutExtension.lastIndexOf("/")+1, fileNameWithoutExtension.length());
				}
				// find the extension
				if (fileHref.contains(".")) {
					fileNameWithoutExtension = fileNameWithoutExtension.substring(0,fileNameWithoutExtension.lastIndexOf("."));
					fileNameExtension = fileHref.substring(fileHref.lastIndexOf("."), fileHref.length());
				}
				
				if (fileNameWithoutExtension.equals(hexString) && strippedFileExtension.equalsIgnoreCase(fileNameExtension)) {
					System.out.println("found file match: " + fileNameWithoutExtension + "::" + hexString);	
					return fileName;
				}
			}
			
			return "";
		}
		
	}

}

