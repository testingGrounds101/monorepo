package org.sakaiproject.importer.impl;

import java.util.Map;
import java.util.HashMap;
import org.w3c.dom.Node;


public class TitleUniquifier {
	private static Map<Node, Map<String, Integer>> namesSeen;
	private static Map<Node, String> nodesSeen;

	// Wraps the original getTitle (now renamed to getTitleHelper) being careful to make sure titles are unique for the same parent.
	public static String uniquify(String title, Node itemNode) {
		if (nodesSeen == null) {
			nodesSeen = new HashMap<Node, String>();
		}

		if (namesSeen == null) {
			namesSeen = new HashMap<Node, Map<String, Integer>>();
		}

		if (nodesSeen.containsKey(itemNode)) {
			// Return our originally calculated value
			return nodesSeen.get(itemNode);
		}

		if (!namesSeen.containsKey(itemNode.getParentNode())) {
			namesSeen.put(itemNode.getParentNode(), new HashMap<String, Integer>());
		}

		Map<String, Integer> titlesAtThisLevel = namesSeen.get(itemNode.getParentNode());

		String suffix = "";

		System.err.println("AT THIS LEVEL " + titlesAtThisLevel);

		if (titlesAtThisLevel.containsKey(title)) {
			int count = titlesAtThisLevel.get(title);
			suffix = String.format(" (%d)", count);
			titlesAtThisLevel.put(title, count + 1);
		} else {
			titlesAtThisLevel.put(title, 2);
		}

		String newTitle = title + suffix;
		nodesSeen.put(itemNode, newTitle);

		return newTitle;
	}
}
