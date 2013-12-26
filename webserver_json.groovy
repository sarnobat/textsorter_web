import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.ws.rs.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		@GET
		@Path("json")
		@Produces("application/json")
		public Response json(@QueryParam("filePath") String iFilePath) throws JSONException,
				IOException {
			System.out.println("readFile() - begin");
			sort: {
				try {
				Defragmenter.defragmentFile(iFilePath);
				System.out.println("readFile() - sort successful");
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
			JSONObject json = new JSONObject();
			File f = new File(iFilePath);
			if (!f.exists()) {
				throw new RuntimeException();
			}
			String contents = FileUtils.readFileToString(f);
			json.put("entireFile", contents);

			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		@POST
		@Path("persist")
		public Response persist(final String body) throws JSONException, IOException,
				URISyntaxException {
			System.out.println("persist() - begin");
			System.out.println(body);
			// Save the changes to the file
			save: {
				List<NameValuePair> params = URLEncodedUtils.parse(new URI("http://www.fake.com/?"
						+ body), "UTF-8");
				Map<String, String> m = new HashMap();
				for (NameValuePair param : params) {
					// System.out.println(param.getName() + " : "
					// + URLDecoder.decode(param.getValue(), "UTF-8"));
					m.put(param.getName(), URLDecoder.decode(param.getValue(), "UTF-8"));
				}
				FileUtils.write(new File(m.get("filePath")), m.get("newFileContents"));
				System.out.println("persist() - write successful");
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString()).type("application/json").build();
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:9099/"), new ResourceConfig(HelloWorldResource.class));
	}


}


class Defragmenter {

	public static final String PUBLISHING = "publishing";

	/**
	 * This only writes it to stdout, it doesn't modify the file.
	 */
	public static void defragmentFile(String fileToOrganizePath) {
		System.out.println("defragmentFile() - begin");
		List<String> lines = TextSorterControllerUtils.readFile(fileToOrganizePath);
		MyTreeNode treeRootNode = TreeCreator.createTreeFromLines(lines);
		MyTreeNode.validateTotalNodeCount(treeRootNode);
		MyTreeNode.printTreeToStringBuffer(treeRootNode, fileToOrganizePath,
				Utils.countNonHeadingLines(lines), new StringBuffer());
		MyTreeNode.resetValidationStats();
	}
}

class Utils {
	public static int determineHeadingLevel(String headingLine) {
		int headingLevel = 0;
		for (int i = 0; i < headingLine.length(); i++) {
			char c = headingLine.charAt(i);
			if (c == '=') {
				++headingLevel;
			} else {
				break;
			}
		}
		return headingLevel;
	}

	public static int countNonHeadingLines(List<String> readFile) {
		int count = 0;
		for (String line : readFile) {
			if (line.trim().length() > 0 && !line.startsWith("=")) {
				++count;
			}
		}
		return count;
	}

	public static String getDeragmentedFilePath(String fragmentedFilePath) {
		return fragmentedFilePath.replace(".mwk", "-sorted.mwk");
	}
}

class TextSorterControllerUtils {
	public static List<String> readFile(String inputFilePath) {
		List<String> theLines = null;
		try {
			theLines = FileUtils.readLines(new File(inputFilePath));

		} catch (IOException e) {
			e.printStackTrace();
		}
		// To ensure we don't lose text before the first heading
		if (!theLines.get(0).startsWith("=")) {
			theLines.add(0, "= =");
		}
		return new CopyOnWriteArrayList<String>(theLines);
	}
}

class TreeCreator {
	@SuppressWarnings("unchecked")
	public static MyTreeNode createTreeFromLines(List<String> lines) {
		List<Snippet> snippets = getSnippetList(lines);
		Stack<MyTreeNode> snippetTreePath = new Stack<MyTreeNode>();
		for (Snippet snippet : snippets) {
			// Find the parent node
			MyTreeNode parentNode = null;
			findParent: {
				// rewind up the path until we find a snippet 1 higher
				// than the
				// current snippet
				MyTreeNode highestNodeInExistingTree = popStackToHighest((Stack<MyTreeNode>) snippetTreePath
						.clone());
				popStackToParent(snippet, snippetTreePath);
				if (!snippetTreePath.isEmpty()) {
					parentNode = snippetTreePath.peek();
				}
				if (parentNode == null) {
					// push a virtual node
					int parentHeadingLevel = snippet.getLevelNumber() - 1;
					parentNode = VirtualNodeCreator.createVirtualNode(parentHeadingLevel,
							(Stack<MyTreeNode>) snippetTreePath.clone(),
							highestNodeInExistingTree);
					snippetTreePath.push(parentNode);
				}
			}
			// Add this snippet as a child of parentNode
			snippetTreePath.push(new MyTreeNode(snippet, parentNode));
			MyTreeNode.validateCount(parentNode);
		}
		// Pop the stack and return the root
		MyTreeNode root = null;
		while (!snippetTreePath.isEmpty()) {
			root = snippetTreePath.pop();
		}
		if (root == null) {
			throw new RuntimeException("Developer Error");
		}
		return root;
	}

	private static void popStackToParent(Snippet snippet, Stack<MyTreeNode> snippetTreePath) {
		if (!snippetTreePath.isEmpty()) {
			while (!snippetTreePath.isEmpty()
					&& snippetTreePath.peek().level() >= snippet.getLevelNumber()) {
				snippetTreePath.pop();
			}
		}
	}

	private static List<Snippet> getSnippetList(List<String> lines) {
		List<Snippet> snippets = new LinkedList<Snippet>();
		int firstHeadingLine = 0;
		while (!isHeadingLine(lines.get(firstHeadingLine))) {
			++firstHeadingLine;
		}
		int nextSnippetStart = 0;
		for (int start = firstHeadingLine; nextSnippetStart < lines.size() - 1; start = Math
				.max(start + 1, nextSnippetStart)) {
			nextSnippetStart = findNextHeadingLineAfter(start, lines);
			if (nextSnippetStart < lines.size() - 1) {
				validate3(lines, nextSnippetStart, start);
			} else {
				++nextSnippetStart;
			}
			validate2(lines, start);
			snippets.add(new Snippet(start, nextSnippetStart, lines));
		}
		return snippets;
	}

	private static void validate3(List<String> lines, int nextSnippetStart, int start) {
		if (!lines.get(nextSnippetStart).matches("^=+.*=+")) {
			throw new RuntimeException("Developer error: [" + start + "] = "
					+ lines.get(nextSnippetStart));
		}
	}

	private static void validate2(List<String> lines, int start) {
		if (!lines.get(start).matches("^=+.*=+")) {
			throw new RuntimeException("Developer error: start is [" + start + "] = "
					+ lines.get(start));
		}
	}

	private static int findNextHeadingLineAfter(final int start, List<String> lines) {
		int nextSnippetStart = start + 1;
		String endLine = lines.get(nextSnippetStart);
		while (!isHeadingLine(endLine)) {
			if (isEndOfFile(nextSnippetStart, lines)) {
				break;
			}
			lines.get(nextSnippetStart);
			endLine = lines.get(++nextSnippetStart);
		}
		String nextHeadingLine = endLine;
		validate(start, lines, nextSnippetStart, nextHeadingLine);
		return nextSnippetStart;
	}

	private static MyTreeNode popStackToHighest(Stack<MyTreeNode> snippetTreePathClone) {
		MyTreeNode highest = null;
		while (!snippetTreePathClone.isEmpty() && snippetTreePathClone.peek() != null) {
			highest = snippetTreePathClone.pop();
		}
		return highest;
	}

	private static void validate(final int start, List<String> lines, int nextSnippetStart,
			String nextHeadingLine) {
		if (!isHeadingLine(nextHeadingLine) && !isEndOfFile(nextSnippetStart, lines)) {
			throw new RuntimeException("Developer error: [" + start + "] = "
					+ nextHeadingLine);
		}
		if (nextSnippetStart <= start) {
			throw new RuntimeException("Developer error: [" + start + ", "
					+ nextSnippetStart + "]");
		}
		if (nextSnippetStart < lines.size() - 1) {
			if (!lines.get(nextSnippetStart).matches("^=+.*=+")) {
				throw new RuntimeException("Developer error: [" + nextSnippetStart + "] = "
						+ lines.get(nextSnippetStart) + ". Last line is " + lines.size());
			}
		}
	}

	private static boolean isEndOfFile(int end, List<String> lines) {
		return end >= lines.size() - 1;

	}

	@Deprecated
	// Use Utils
	private static boolean isHeadingLine(String line) {
		return line.matches("^=+.*=+");
	}

}

class VirtualNodeCreator {

	public static MyTreeNode createVirtualNode(int iHeadingLevel,
			Stack<MyTreeNode> snippetTreePath, MyTreeNode highestNodeInExistingTree) {
		boolean attachExistingTreeToNewNode = false;
		MyTreeNode parentNode = null;
		// attach to existing tree
		if (highestNodeInExistingTree != null) {
			if (highestNodeInExistingTree.level() == iHeadingLevel) {
				throw new RuntimeException("Not sure how to handle this case. Recursive?");
			} else if (highestNodeInExistingTree.level() < iHeadingLevel) {
				parentNode = findFosterParent(iHeadingLevel, snippetTreePath);
			} else if (highestNodeInExistingTree.level() > iHeadingLevel) {
				// attach the highest node as a child to the new virtual
				// node
				// (is it necessary to create a virtual node then?)
				attachExistingTreeToNewNode = true;
			}
		}
		MyTreeNode n = new MyTreeNode(createVirtualSnippet(iHeadingLevel), parentNode);
		if (attachExistingTreeToNewNode) {
			attachExistingTreeToNode(highestNodeInExistingTree, n);
		}
		return n;
	}

	private static void attachExistingTreeToNode(MyTreeNode highestNodeInExistingTree,
			MyTreeNode n) {
		if (highestNodeInExistingTree.level() + 1 != n.level()) {
			System.err.println("Ideally this should never happen");
		}
		n.addChild(highestNodeInExistingTree);
	}

	private static Snippet createVirtualSnippet(int parentHeadingLevel) {
		StringBuffer equalsLeg = new StringBuffer();
		for (int i = 0; i < parentHeadingLevel; i++) {
			equalsLeg.append("=");
		}
		String string = new StringBuffer().append(equalsLeg).append(" ").append(equalsLeg)
				.toString();
		return new Snippet(ImmutableList.of(string), string, parentHeadingLevel);
	}

	private static MyTreeNode findFosterParent(int parentHeadingLevel,
			Stack<MyTreeNode> snippetTreePathClone) {
		while (snippetTreePathClone.peek().level() >= parentHeadingLevel) {
			snippetTreePathClone.pop();
		}
		return snippetTreePathClone.peek();
	}
}

class Snippet implements Comparable {
	final int levelNumber;
	final List<String> snippetLines;
	private final String headingLine;

	public Snippet(List<String> singleSnippetLines, String headingLine, int headingLevel) {
		this.snippetLines = singleSnippetLines;
		this.levelNumber = headingLevel;
		this.headingLine = headingLine;
	}

	public Snippet(int start, int nextSnippetStart, List<String> allFileLines) {
		this(getSnippetLines(start, nextSnippetStart, allFileLines), getHeadingLine(start,
				allFileLines), Utils.determineHeadingLevel(getHeadingLine(start,
				allFileLines)));
	}

	public static String getHeadingLine(int start, List<String> allFileLines) {
		validateIsHeadingLine(allFileLines.get(start));
		return allFileLines.get(start);
	}

	private static void validateIsHeadingLine(String headingLine) {
		if (!headingLine.matches("^=+.*=+")) {
			throw new RuntimeException("Developer error: Not a heading line: "
					+ headingLine);
		}
	}

	public static ImmutableList<String> getSnippetLines(int start, int nextSnippetStart,
			List<String> allFileLines) {
		if (allFileLines.size() <= nextSnippetStart) {
			// make sure the final line gets included in this snippet
			nextSnippetStart = allFileLines.size();
		}
		validateSnippetStartAndNextAreNotSame(start, nextSnippetStart);
		return ImmutableList.copyOf(allFileLines.subList(start,
				Math.max(start + 1, nextSnippetStart)));
	}

	private static void validateSnippetStartAndNextAreNotSame(int start,
			int nextSnippetStart) {
		if (start == nextSnippetStart) {
			throw new RuntimeException("end should be the start of the next");
		}
	}

	public int getLevelNumber() {
		return levelNumber;
	}

	public String getHeadingLine() {
		return headingLine;
	}

	@Override
	public String toString() {
		return getText().toString();
	}

	public StringBuffer getText() {
		StringBuffer sb = new StringBuffer();
		for (String line : snippetLines) {
			sb.append(line);
			sb.append("\n");
		}
		return sb;
	}

	// @Override
	public int compareTo(Object o) {
		Snippet that = (Snippet) o;
		String thisHeading = this.headingLine.toLowerCase();
		String thatHeading = that.headingLine.toLowerCase();
		return thisHeading.compareTo(thatHeading);
	}

	@Override
	public boolean equals(Object o) {
		String thisText = this.getText().toString();
		String thatText = ((Snippet) o).getText().toString();
		if (thisText.equals(thatText)) {
			if (thisText != thatText) {
				throw new RuntimeException("This case is not considered");
			}
		}
		return thisText.equals(thatText);
	}
}

class MyTreeNode implements Comparable {
	final ListMultimap<String, MyTreeNode> childNodes = LinkedListMultimap.create();
	final MyTreeNode parentNode;
	final Snippet snippet;
	public static int totalNodeCount = 0;

	// parentNode could be null
	public MyTreeNode(Snippet currentSnippet, MyTreeNode parentNode) {
		this.snippet = currentSnippet;
		this.parentNode = parentNode;
		if (parentNode != null) {
			parentNode.addChild(this);
		}
		++totalNodeCount;
		System.out.println(currentSnippet.getHeadingLine());
	}

	// Only necessary for virtual nodes. Otherwise there's no need to
	// call this.
	// Child-parent relationships should be established in the
	// constructor
	// itself
	void addChild(MyTreeNode currentNode) {
		validateIsNotParentOf(currentNode);
		int sizeBefore = childNodes.get(currentNode.getSnippetHeadingLine()).size();
		if (sizeBefore > 0) {
			if (currentNode.getSnippetText().equals(
					childNodes.get(currentNode.getSnippetHeadingLine()).iterator().next())) {
				throw new RuntimeException("Developer Error");
			}
		}
		childNodes.put(currentNode.getSnippetHeadingLine(), currentNode);
		validateSizeBeforeAndAfterNotSame(currentNode, sizeBefore);

	}

	private void validateIsNotParentOf(MyTreeNode currentNode) {
		if (currentNode.isParentOf(this)) {
			throw new RuntimeException("Cycle detected");
		}
	}

	private void validateSizeBeforeAndAfterNotSame(MyTreeNode currentNode, int sizeBefore) {
		int sizeAfter = childNodes.get(currentNode.getSnippetHeadingLine()).size();
		if (sizeBefore == sizeAfter) {
			System.out.println("####################################");
			System.out.println(childNodes.get(currentNode.getSnippetHeadingLine())
					.iterator().next().getSnippetText());
			System.out.println("##################");
			System.out.println(currentNode.getSnippetText());
			throw new RuntimeException("Developer Error");
		}
	}

	// public Snippet getSnippet() {
	// return snippet;
	// }
	public String getSnippetHeadingLine() {
		return snippet.getHeadingLine();
	}

	public int level() {
		return snippet.getLevelNumber();
	}

	public StringBuffer getSnippetText() {
		return snippet.getText();
	}

	private ImmutableList<MyTreeNode> getChildNodes() {
		List<MyTreeNode> l = new java.util.LinkedList<MyTreeNode>();
		for (String key : childNodes.keySet()) {
			List<MyTreeNode> nodesWithSameHeading = childNodes.get(key);
			l.addAll(nodesWithSameHeading);
		}
		if (!preserveOriginalOrder(this)) {
//			System.out.println();
//			System.out.println("============= before ==================");
//			printSortedHeadings(l);
			Collections.sort(l);
//			printSortedHeadings(l);
//			System.out.println("============= after ==================");
//			System.out.println();

		}
		ImmutableList<MyTreeNode> ret = ImmutableList.copyOf(l);
		return ret;
	}

	public void printSortedHeadings(List<MyTreeNode> l) {
		if (l.size() > 1) {
			System.out.println("SORTING: -------------- begin -----------------");
			for (MyTreeNode n : l) {
				System.out.println("SORTING: " + n.getHeadingText());
			}
			System.out.println("SORTING: ---------------- end ---------------");
		}
	}

	private Boolean preserveOriginalOrder(MyTreeNode myTreeNode1) {
		boolean preserveOriginalOrder = myTreeNode1.getSnippetHeadingLine().contains(
				"do not sort") || myTreeNode1.getSnippetHeadingLine().contains(Defragmenter.PUBLISHING);
		if (preserveOriginalOrder) {
			return true;
		}
		if (myTreeNode1.parentNode == null) {
			return false;
		}
		return this.preserveOriginalOrder(myTreeNode1.getParentNode());

	}

	@Override
	public String toString() {
		return getSnippetText().toString();
	}

	public MyTreeNode getParentNode() {
	 	return parentNode;
 	}

	// @Override
	public int compareTo(Object other) {
		MyTreeNode that = (MyTreeNode) other;
		int ret = this.snippet.compareTo(that.snippet);
		if (ret == 0) {
			// throw new
			// RuntimeException("snippets should never be equal");
		}
		return ret;
	}

	public String getHeadingText() {
		return snippet.getHeadingLine();
	}

	public boolean isParentOf(MyTreeNode iNode) {

		if (iNode.parentNode == this) {
			return true;
		}

		if (iNode.parentNode == null) {
			return false;
		}
		return this.isParentOf(iNode.parentNode);

	}

	public int countNodesInSubtree() {
		int count = 1;
		for (MyTreeNode child : this.getChildNodes()) {
			count += child.countNodesInSubtree();
		}
		return count;
	}

	public void addChildren(List<MyTreeNode> roots) {
		for (MyTreeNode aRoot : roots) {
			this.addChild(aRoot);
		}
	}

	public static int countAllNodesInTree(MyTreeNode anyNodeInTree) {
		// Find the highest parent
		MyTreeNode root = getHighestNode(anyNodeInTree);
		return root.countNodesInSubtree();
	}

	private static MyTreeNode getHighestNode(MyTreeNode anyNodeInTree) {
		if (anyNodeInTree.parentNode == null) {
			return anyNodeInTree;
		} else {
			return getHighestNode(anyNodeInTree.parentNode);
		}
	}

	private static MyTreeNode printSubtree(MyTreeNode root, StringBuffer sb) {
		if (root.getSnippetHeadingLine().contains(Defragmenter.PUBLISHING)) {
			return root;
		}
		sb.append(root.getSnippetText());
		MyTreeNode forPublishing = null;
		for (MyTreeNode child : root.getChildNodes()) {
			MyTreeNode forPublishingChild = printSubtree(child, sb);
			if (forPublishingChild != null) {
				// sanity check
				if (forPublishing != null) {
					throw new RuntimeException(
							"Developer error - we cannot support more than one tree for publishing");
				}
				forPublishing = forPublishingChild;
			}
		}
		return forPublishing;
	}

	public static void printTreeToStringBuffer(MyTreeNode root, String fileToOrganizePath,
			int nonHeadinglinesInOriginalFile, StringBuffer sb) {
		MyTreeNode forPublishing = MyTreeNode.printSubtree(root, sb);
		if (forPublishing != null) {
			printPublishedSubtree(forPublishing, sb);
		}
		try {
			String outputPath = Utils.getDeragmentedFilePath(fileToOrganizePath);
			FileUtils.write(new File(outputPath), sb.toString());
			int after = Utils.countNonHeadingLines(TextSorterControllerUtils
					.readFile(outputPath));
			if (nonHeadinglinesInOriginalFile > after) {
				throw new RuntimeException("Lines lost");
			} else {
				System.out.println("Before, after: [" + nonHeadinglinesInOriginalFile
						+ ", " + after + "]");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void printPublishedSubtree(MyTreeNode forPublishing, StringBuffer sb) {
		sb.append(forPublishing.getSnippetText());
		for (MyTreeNode child : forPublishing.getChildNodes()) {
			printPublishedSubtree(child, sb);
		}
	}

	public static void validateCount(MyTreeNode parentNode) {
		if (MyTreeNode.countAllNodesInTree(parentNode) < MyTreeNode.totalNodeCount) {
			throw new RuntimeException("Disconnected from parent: " + parentNode);
		}
	}

	public static void validateTotalNodeCount(MyTreeNode root) {
		int subtreeNodeCount = root.countNodesInSubtree();
		if (subtreeNodeCount < MyTreeNode.totalNodeCount) {
			throw new RuntimeException("Nodes lost: [" + MyTreeNode.totalNodeCount + ", "
					+ subtreeNodeCount + "]");
		}
	}

	public static void resetValidationStats() {
		totalNodeCount = 0;
	}

}

