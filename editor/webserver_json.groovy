import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

//import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.sun.net.httpserver.HttpServer;

public class TextSorterWebServer {
	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(new URI(
				"http://localhost:9098/"), new ResourceConfig(
				HelloWorldResource.class));

	}

	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		@GET
		@Path("json")
		@Produces("application/json")
		public Response json(@QueryParam("filePath") String iFilePath)
				throws JSONException, IOException {
			System.out.println("readFile() - begin");
			createSortedCopyOfFile(iFilePath);
			JSONObject json = getJsonForMwkFile(iFilePath);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(json.toString()).type("application/json").build();
		}

		private JSONObject getJsonForMwkFile(String iFilePath)
				throws IOException {
			JSONObject json = new JSONObject();

			File mwkFile = new File(iFilePath);
			if (!mwkFile.exists()) {
				throw new RuntimeException();
			}
			String contents = FileUtils.readFileToString(mwkFile);
			json.put("entireFile", contents);

			return json;
		}

		private void createSortedCopyOfFile(String iFilePath) {
			try {
				Defragmenter.defragmentFile(iFilePath);
				System.out.println("readFile() - sort successful");
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
		}

		@POST
		@Path("persist")
		public Response persist(final String body) throws JSONException,
				IOException, URISyntaxException {
			System.out.println("persist() - begin");
			// Save the changes to the file
			save: {
				Map<String, String> params = getParamsMap(body);
				String filePath = params.get("filePath");
				String newFileContents = params.get("newFileContents");
				writeStringToFile(filePath, newFileContents);
				System.out.println("persist() - write successful");
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString())
					.type("application/json").build();
		}

		private void writeStringToFile(String iMwkFilePath, String iFileContents)
				throws IOException {
			FileUtils.write(new File(iMwkFilePath), iFileContents);
		}

		private Map<String, String> getParamsMap(final String body)
				throws URISyntaxException, UnsupportedEncodingException {
			List<NameValuePair> params = URLEncodedUtils.parse(new URI(
					"http://www.fake.com/?" + body), "UTF-8");
			Map<String, String> m = new HashMap<String, String>();
			for (NameValuePair param : params) {
				// System.out.println(param.getName() + " : "
				// + URLDecoder.decode(param.getValue(), "UTF-8"));
				m.put(param.getName(),
						URLDecoder.decode(param.getValue(), "UTF-8"));
			}
			return m;
		}
	}

}

class Defragmenter {

	public static final String PUBLISHING = "publishing";

	/**
	 * This only writes it to stdout, it doesn't modify the file.
	 */
	public static void defragmentFile(String fileToOrganizePath) {
		System.out.println("defragmentFile() - begin");
		List<String> lines = TextSorterControllerUtils
				.readFile(fileToOrganizePath);
		MyTreeNode treeRootNode = TreeCreator.createTreeFromLines(lines);
		MyTreeNode.validateTotalNodeCount(treeRootNode);
		MyTreeNode.dumpTreeToFileAndVerify(treeRootNode, fileToOrganizePath,
				Utils.countNonHeadingLines(lines));
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
		List<Snippet> theSnippetList = getSnippetList(lines);
		Stack<MyTreeNode> snippetTreePath = new Stack<MyTreeNode>();
		MyTreeNode.totalNodeCount = 0;
		for (Snippet aSnippet : theSnippetList) {
			// Find the parent node
			MyTreeNode aParentNode = null;
			findParentForCurrentSnippet: {
				// rewind up the path until we find a snippet 1 higher
				// than the current snippet
				MyTreeNode highestNodeInExistingTree = popStackToHighest((Stack<MyTreeNode>) snippetTreePath
						.clone());
				popStackToParent(aSnippet, snippetTreePath);

				if (!snippetTreePath.isEmpty()) {
					aParentNode = snippetTreePath.peek();
				}
				if (aParentNode == null) {
					// push a virtual node
					int parentHeadingLevel = aSnippet.getLevelNumber() - 1;
					aParentNode = VirtualNodeCreator.createVirtualNode(
							parentHeadingLevel,
							(Stack<MyTreeNode>) snippetTreePath.clone(),
							highestNodeInExistingTree);
					snippetTreePath.push(aParentNode);
				}
			}
			// Add this snippet as a child of parentNode
			snippetTreePath.push(new MyTreeNode(aSnippet, aParentNode));
			// MyTreeNode.dumpTree(aParentNode);
			MyTreeNode.validateCount(aParentNode);
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

	private static void popStackToParent(Snippet snippet,
			Stack<MyTreeNode> snippetTreePath) {
		if (!snippetTreePath.isEmpty()) {
			while (!snippetTreePath.isEmpty()
					&& snippetTreePath.peek().level() >= snippet
							.getLevelNumber()) {
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

	private static void validate3(List<String> lines, int nextSnippetStart,
			int start) {
		if (!lines.get(nextSnippetStart).matches("^=+.*=+")) {
			throw new RuntimeException("Developer error: [" + start + "] = "
					+ lines.get(nextSnippetStart));
		}
	}

	private static void validate2(List<String> lines, int start) {
		if (!lines.get(start).matches("^=+.*=+")) {
			throw new RuntimeException("Developer error: start is [" + start
					+ "] = " + lines.get(start));
		}
	}

	private static int findNextHeadingLineAfter(final int start,
			List<String> lines) {
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

	private static MyTreeNode popStackToHighest(
			Stack<MyTreeNode> snippetTreePathClone) {
		MyTreeNode highest = null;
		while (!snippetTreePathClone.isEmpty()
				&& snippetTreePathClone.peek() != null) {
			highest = snippetTreePathClone.pop();
		}
		return highest;
	}

	private static void validate(final int start, List<String> lines,
			int nextSnippetStart, String nextHeadingLine) {
		if (!isHeadingLine(nextHeadingLine)
				&& !isEndOfFile(nextSnippetStart, lines)) {
			throw new RuntimeException("Developer error: [" + start + "] = "
					+ nextHeadingLine);
		}
		if (nextSnippetStart <= start) {
			throw new RuntimeException("Developer error: [" + start + ", "
					+ nextSnippetStart + "]");
		}
		if (nextSnippetStart < lines.size() - 1) {
			if (!lines.get(nextSnippetStart).matches("^=+.*=+")) {
				throw new RuntimeException("Developer error: ["
						+ nextSnippetStart + "] = "
						+ lines.get(nextSnippetStart) + ". Last line is "
						+ lines.size());
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
			Stack<MyTreeNode> snippetTreePath,
			MyTreeNode highestNodeInExistingTree) {
		boolean attachExistingTreeToNewNode = false;
		MyTreeNode parentNode = null;
		// attach to existing tree
		if (highestNodeInExistingTree != null) {
			if (highestNodeInExistingTree.level() == iHeadingLevel) {
				throw new RuntimeException(
						"Not sure how to handle this case. Recursive?");
			} else if (highestNodeInExistingTree.level() < iHeadingLevel) {
				parentNode = findFosterParent(iHeadingLevel, snippetTreePath);
			} else if (highestNodeInExistingTree.level() > iHeadingLevel) {
				// attach the highest node as a child to the new virtual
				// node
				// (is it necessary to create a virtual node then?)
				attachExistingTreeToNewNode = true;
			}
		}
		MyTreeNode n = new MyTreeNode(createVirtualSnippet(iHeadingLevel),
				parentNode);
		if (attachExistingTreeToNewNode) {
			attachExistingTreeToNode(highestNodeInExistingTree, n);
		}
		return n;
	}

	private static void attachExistingTreeToNode(
			MyTreeNode highestNodeInExistingTree, MyTreeNode n) {
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
		String string = new StringBuffer().append(equalsLeg).append(" ")
				.append(equalsLeg).toString();
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

class Snippet implements Comparable<Object> {
	final int levelNumber;
	final List<String> snippetLines;
	private final String headingLine;

	public Snippet(List<String> singleSnippetLines, String headingLine,
			int headingLevel) {
		this.snippetLines = Preconditions.checkNotNull(singleSnippetLines);
		this.levelNumber = headingLevel;
		this.headingLine = Preconditions.checkNotNull(headingLine);
	}

	public Snippet(int start, int nextSnippetStart, List<String> allFileLines) {
		this(getSnippetLines(start, nextSnippetStart, allFileLines),
				getHeadingLine(start, allFileLines), Utils
						.determineHeadingLevel(getHeadingLine(start,
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

	public static ImmutableList<String> getSnippetLines(int start,
			int nextSnippetStart, List<String> allFileLines) {
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

	public StringBuffer getTextNoHeading() {
		StringBuffer sb = new StringBuffer();

		for (int i = 1; i < snippetLines.size(); i++) {
			sb.append(snippetLines.get(i));
			sb.append("\n");
		}
		return sb;
	}

	public StringBuffer getText() {
		StringBuffer sb = new StringBuffer();
		int i = 0;
		for (String line : snippetLines) {
			sb.append(line);
			// if (i == snippetLines.size()-1) {
			// if (line.equals("\n")) {
			//
			// }
			// sb.append("\n");
			// } else {
			sb.append("\n");
			// }
			i++;
		}
		return sb;
	}

	// @Override
	public int compareTo(Object o) {
		if (o == null) {
			return 1;
		}
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

class MyTreeNode implements Comparable<Object> {
	final ListMultimap<String, MyTreeNode> childNodes = LinkedListMultimap
			.create();
	final MyTreeNode parentNode;
	final Snippet snippet;
	public static int totalNodeCount = 0;

	// parentNode could be null
	public MyTreeNode(Snippet currentSnippet, MyTreeNode parentNode) {
		if (parentNode == this) {
			throw new RuntimeException(
					"Developer Error - can't be parent of self");
		}
		if (parentNode != null) {
			if (parentNode.getSnippetHeadingLine().equals(
					currentSnippet.getHeadingLine())) {
				String s = parentNode.getSnippetHeadingLine() + "::"
						+ currentSnippet.getHeadingLine();
				throw new RuntimeException(
						"Developer Error - can't be parent of self: " + s);
			}
		}
		this.snippet = currentSnippet;
		this.parentNode = parentNode;
		if (parentNode != null) {
			parentNode.addChild(this);
		}
		++totalNodeCount;
		// System.out.println(currentSnippet.getHeadingLine());
	}

	// Only necessary for virtual nodes. Otherwise there's no need to
	// call this.
	// Child-parent relationships should be established in the
	// constructor
	// itself
	void addChild(MyTreeNode currentNode) {
		validateIsNotParentOf(currentNode);
		int sizeBefore = childNodes.get(currentNode.getSnippetHeadingLine())
				.size();
		if (sizeBefore > 0) {
			if (currentNode.getSnippetText().equals(
					childNodes.get(currentNode.getSnippetHeadingLine())
							.iterator().next())) {
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

	private void validateSizeBeforeAndAfterNotSame(MyTreeNode currentNode,
			int sizeBefore) {
		int sizeAfter = childNodes.get(currentNode.getSnippetHeadingLine())
				.size();
		if (sizeBefore == sizeAfter) {
			System.out.println("####################################");
			System.out.println(childNodes
					.get(currentNode.getSnippetHeadingLine()).iterator().next()
					.getSnippetText());
			System.out.println("##################");
			System.out.println(currentNode.getSnippetText());
			throw new RuntimeException("Developer Error");
		}
	}

	// public Snippet getSnippet() {
	// return snippet;
	// }
	String getSnippetHeadingLine() {
		return snippet.getHeadingLine();
	}

	int level() {
		return snippet.getLevelNumber();
	}

	@Deprecated
	// Demeter
	Snippet getSnippet() {
		return snippet;
	}

	StringBuffer getSnippetText() {
		return snippet.getText();
	}

	StringBuffer getSnippetTextNoHeading() {
		return snippet.getTextNoHeading();
	}

	private ImmutableList<MyTreeNode> getChildNodes() {
		List<MyTreeNode> l = new java.util.LinkedList<MyTreeNode>();
		for (String key : childNodes.keySet()) {
			List<MyTreeNode> nodesWithSameHeading = childNodes.get(key);
			l.addAll(nodesWithSameHeading);
		}
		if (!preserveOriginalOrder(this)) {
			Collections.sort(l);
		}
		ImmutableList<MyTreeNode> ret = ImmutableList.copyOf(l);
		return ret;
	}

	private Boolean preserveOriginalOrder(MyTreeNode myTreeNode1) {
		boolean preserveOriginalOrder = myTreeNode1.getSnippetHeadingLine()
				.contains("do not sort") || myTreeNode1.getSnippetHeadingLine().contains(
						Defragmenter.PUBLISHING);
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
		return print("");
	}

	private String print(String indent) {
		String children = "";
		for (MyTreeNode child : childNodes.values()) {
			children += child.print(indent + "\t");
		}
		return indent + getHeadingText() + (childNodes.size() > 0 ? "" : "")
				+ children;
	}

	MyTreeNode getParentNode() {
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

	String getHeadingText() {
		return snippet.getHeadingLine();
	}

	boolean isParentOf(MyTreeNode iNode) {

		if (iNode.parentNode == this) {
			return true;
		}

		if (iNode.parentNode == null) {
			return false;
		}
		return this.isParentOf(iNode.parentNode);

	}

	int countNodesInSubtree() {
		int count = 1;
		for (MyTreeNode child : this.getChildNodes()) {
			count += child.countNodesInSubtree();
		}
		return count;
	}

	void addChildren(List<MyTreeNode> roots) {
		for (MyTreeNode aRoot : roots) {
			this.addChild(aRoot);
		}
	}

	static int countAllNodesInTree(MyTreeNode anyNodeInTree) {
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

	// TODO: Bad. A method that has side effects AND returns something. This is
	// why this algorithm is difficult to understand
	private static MyTreeNode printNonPublishedSectionsOfSubtree(
			MyTreeNode iTreeRoot, StringBuffer outputString) {
		if (iTreeRoot.getSnippetHeadingLine().contains(Defragmenter.PUBLISHING)) {
			return iTreeRoot;
		}
		outputString.append(iTreeRoot.getSnippetText());
		MyTreeNode forPublishing = null;
		for (MyTreeNode child : iTreeRoot.getChildNodes()) {
			MyTreeNode forPublishingChild = printNonPublishedSectionsOfSubtree(
					child, outputString);
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

	static void dumpTreeToFileAndVerify(MyTreeNode iRootTreeNode,
			String iFileToWritePath, int nonHeadinglinesInOriginalFile) {
		String outputMwkFilePath = Utils
				.getDeragmentedFilePath(iFileToWritePath);

		writeTreeToFile2(iRootTreeNode, outputMwkFilePath);
		verifyFileWasWritten(outputMwkFilePath, nonHeadinglinesInOriginalFile);

		removeRedundantHeadings(outputMwkFilePath,
				nonHeadinglinesInOriginalFile);
	}

	private static void verifyFileWasWritten(String outputPath,
			int nonHeadinglinesInOriginalFile) {

		int after = Utils.countNonHeadingLines(TextSorterControllerUtils
				.readFile(outputPath));
		if (nonHeadinglinesInOriginalFile > after) {
			throw new RuntimeException("Lines lost");
		} else {
			System.out.println("writeTreeToFile() - Before, after: ["
					+ nonHeadinglinesInOriginalFile + ", " + after + "]");
		}

	}

	static StringBuffer dumpTree(MyTreeNode root) {
		StringBuffer rStringBuffer = new StringBuffer();

		// Step 1 - print the non-published sections
		MyTreeNode forPublishing = MyTreeNode
				.printNonPublishedSectionsOfSubtree(root, rStringBuffer);

		// Step 2 - print the published section
		if (forPublishing != null) {
			printPublishedSubtree(forPublishing, rStringBuffer);
		}

		return rStringBuffer;
	}

	private static void removeRedundantHeadings(String uncoagulatedFilePath,
			int nonHeadinglinesInOriginalFile) {
		String coagulatedFilePath = getCoagulatedFilePath(uncoagulatedFilePath);

		MyTreeNode inputTreeRootNode = createTreeFromMwkFile(uncoagulatedFilePath);

		MyTreeNode outputTreeRootNode = coagulateChildrenOfRootNode(inputTreeRootNode);

		validateNodeAfterCoagulationOfChildren(inputTreeRootNode,
				outputTreeRootNode);

		writeTreeToFile(outputTreeRootNode, coagulatedFilePath);

		validateSizeBeforeAndAfterCoagulation(coagulatedFilePath,
				nonHeadinglinesInOriginalFile);
	}

	@Deprecated
	// Use TextSorterWebServer#writeTreeToFile()
	private static void writeTreeToFile2(MyTreeNode iTreeRootNode,
			String iMwkFilePath) {
		StringBuffer theEntireFileString = dumpTree(iTreeRootNode);
		writeStringToFile(theEntireFileString, iMwkFilePath);
	}

	private static void writeTreeToFile(MyTreeNode iTreeRootNode,
			String iMwkFilePath) {
		StringBuffer theEntireFileString = dumpTree(iTreeRootNode);
		writeStringToFile(theEntireFileString, iMwkFilePath);
	}

	private static void writeStringToFile(StringBuffer sb, String iFilePath) {
		try {
			FileUtils.write(new File(iFilePath), sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static MyTreeNode createTreeFromMwkFile(String iMwkFilePath) {
		try {
			List<String> lines = FileUtils.readLines(new File(iMwkFilePath));

			MyTreeNode inputTreeRootNode = TreeCreator
					.createTreeFromLines(lines);
			return inputTreeRootNode;
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}

	private static void validateSizeBeforeAndAfterCoagulation(
			String coagulatedFilePath, int nonHeadinglinesInOriginalFile) {

		int after = Utils.countNonHeadingLines(TextSorterControllerUtils
				.readFile(coagulatedFilePath));
		System.out.println("removeRedundantHeadings() - Before, after: ["
				+ nonHeadinglinesInOriginalFile + ", " + after + "]");
		if (nonHeadinglinesInOriginalFile != after) {
			throw new RuntimeException("Lines lost or spurious added");
		} else {
		}
	}

	private static void validateNodeAfterCoagulationOfChildren(
			MyTreeNode inputTreeNode, MyTreeNode outputTreeNode) {
		if (inputTreeNode.getParentNode() == null
				&& outputTreeNode.getParentNode() == null) {
			return;
		}
		if (inputTreeNode.getParentNode().getHeadingText()
				.equals(outputTreeNode.getParentNode().getHeadingText())) {
			return;
		}
		if (inputTreeNode.getChildNodes().size() > 0) {
			if (!(outputTreeNode.getChildNodes().size() > 0)) {
				return;
			}
		}
		throw new RuntimeException("Something got lost during coagulation");
	}

	public static String getCoagulatedFilePath(String fragmentedFilePath) {
		return fragmentedFilePath.replace("sorted", "coagulate");
	}

	/**
	 * Creates a mirror of the passed subtree, but with children combined into a
	 * single node
	 */
	private static MyTreeNode coagulateChildrenOfRootNode(
			MyTreeNode iInputTreeRootNode) {

		MyTreeNode rOutputTreeNode = cloneRootNodeHeader(iInputTreeRootNode);
		Map<String, List<MyTreeNode>> childNodesGroupedByHeading = getRootChildNodesGrouped(iInputTreeRootNode
				.getChildNodes());
		Map<String, MyTreeNode> superChildNodes = createSuperNodesForRootChildren(childNodesGroupedByHeading);
		// Ideally we should sort these
		addChildNodesToOutputRootNode(rOutputTreeNode, superChildNodes.values());
		return rOutputTreeNode;
	}

	private static MyTreeNode cloneRootNodeHeader(MyTreeNode iInputTreeRootNode) {
		// TODO: override clone method instead?
		MyTreeNode clone = new MyTreeNode(iInputTreeRootNode.getSnippet(),
				iInputTreeRootNode.getParentNode());
		return clone;
	}

	private static Map<String, List<MyTreeNode>> getRootChildNodesGrouped(
			List<MyTreeNode> childNodes2) {
		return groupByHeading(childNodes2);
	}

	private static void addChildNodesToOutputRootNode(
			MyTreeNode rOutputTreeNode, Collection<MyTreeNode> values) {
		rOutputTreeNode.addChildren(new LinkedList<>(values));

	}

	private static Map<String, MyTreeNode> createSuperNodesForRootChildren(
			Map<String, List<MyTreeNode>> nodesGroupedByHeading) {
		Map<String, MyTreeNode> ret = new HashMap<String, MyTreeNode>();
		if (nodesGroupedByHeading == null) {
			return ret;
		}
		for (String aHeading : nodesGroupedByHeading.keySet()) {
			List<MyTreeNode> allNodesForHeading = nodesGroupedByHeading
					.get(aHeading);
			MyTreeNode singleNode = squashNodes(allNodesForHeading);
			ret.put(aHeading, singleNode);
		}
		return ret;
	}

	private static MyTreeNode squashNodes(List<MyTreeNode> allNodesForHeading) {

		MyTreeNode squashedNode = createSuperNodeHeader(allNodesForHeading);

		List<MyTreeNode> roots = getSuperNodes(allNodesForHeading);
		squashedNode.addChildren(roots);

		return squashedNode;
	}

	private static List<MyTreeNode> getSuperNodes(
			List<MyTreeNode> allNodesForHeading) {
		List<MyTreeNode> children = getAllChildren(allNodesForHeading);

		Map<String, List<MyTreeNode>> childrenByHeading = groupByHeading(children);
		Map<String, MyTreeNode> superChildrenByHeading = squashNodesByHeading(childrenByHeading);
		// Ideally we should add these children in sorted order
		List<MyTreeNode> roots = new LinkedList<MyTreeNode>(
				superChildrenByHeading.values());
		return roots;
	}

	private static Map<String, MyTreeNode> squashNodesByHeading(
			Map<String, List<MyTreeNode>> nodesByHeading) {
		Map<String, MyTreeNode> ret = new HashMap<String, MyTreeNode>();
		for (String aHeading : nodesByHeading.keySet()) {
			List<MyTreeNode> nodes = nodesByHeading.get(aHeading);
			MyTreeNode superNode = createSuperNodeHeader(nodes);
			List<MyTreeNode> superChildNodes = getSuperNodes(nodes);
			superNode.addChildren(superChildNodes);
			ret.put(aHeading, superNode);
		}
		return ret;
	}

	//@Nullable
	private static MyTreeNode createSuperNodeHeader(List<MyTreeNode> nodes) {
		if (nodes.size() == 0) {
			return null;
		}
		checkAllNodesHaveSameHeading(nodes);
		MyTreeNode first = nodes.get(0);
		MyTreeNode parent = first.getParentNode();
		List<String> superSnippetLines = new LinkedList<>();
		int i = 0;
		for (MyTreeNode n : nodes) {
			// Hmmm we're assuming that a string containing newlines will have
			// the same effect as newline-deliminted line. Hopefully this won't
			// be a problem.
			StringBuffer textToAdd;
			// TODO: Ideally use this, but since note making has not been
			// disciplined, this will lose separations.
			// if (i == 0) {
			textToAdd = n.getSnippetText();
			String textToAdd2 = textToAdd.substring(0, textToAdd.length() - 1);
			// } else {

			// textToAdd = n.getSnippetTextNoHeading();
			// }
			superSnippetLines.add(textToAdd2.toString());
			i++;
		}
		Snippet superSnippet = new Snippet(superSnippetLines,
				first.getHeadingText(), first.getSnippet().getLevelNumber());
		MyTreeNode ret = new MyTreeNode(superSnippet, parent);
		return ret;
	}

	private static void checkAllNodesHaveSameHeading(List<MyTreeNode> nodes) {
		if (nodes.size() == 0) {
			return;
		}
		String heading = nodes.get(0).getHeadingText();
		for (MyTreeNode n : nodes) {
			if (!heading.equals(n.getHeadingText())) {
				throw new RuntimeException(heading + "::" + n.getHeadingText());
			}
		}

	}

	private static List<MyTreeNode> getAllChildren(
			List<MyTreeNode> iNodesForHeading) {
		List<MyTreeNode> superList = new LinkedList<MyTreeNode>();
		for (MyTreeNode aChildNode : iNodesForHeading) {
			superList.addAll(aChildNode.getChildNodes());
		}
		return superList;
	}

	private static Map<String, List<MyTreeNode>> groupByHeading(
			List<MyTreeNode> iNodes) {
		Map<String, List<MyTreeNode>> rHeadingToAllChildrenOfHeading = new HashMap<String, List<MyTreeNode>>();

		for (MyTreeNode childNode : iNodes) {
			List<MyTreeNode> childNodesForHeading = rHeadingToAllChildrenOfHeading
					.get(childNode.getHeadingText());
			if (childNodesForHeading == null) {
				childNodesForHeading = new LinkedList<>();
				rHeadingToAllChildrenOfHeading.put(childNode.getHeadingText(),
						childNodesForHeading);
			}
			childNodesForHeading.add(childNode);
		}
		return rHeadingToAllChildrenOfHeading;
	}

	private static void printPublishedSubtree(MyTreeNode forPublishing,
			StringBuffer sb) {
		sb.append(forPublishing.getSnippetText());
		for (MyTreeNode child : forPublishing.getChildNodes()) {
			printPublishedSubtree(child, sb);
		}
	}

	public static void validateCount(MyTreeNode parentNode) {
		Preconditions.checkNotNull(parentNode);
		int countAllNodesInTree = MyTreeNode.countAllNodesInTree(parentNode);
		int totalNodeCount2 = MyTreeNode.totalNodeCount;
		if (countAllNodesInTree < totalNodeCount2) {
			throw new RuntimeException("Disconnected from parent: "
					+ countAllNodesInTree + " vs " + totalNodeCount2 + "\n\n"
					+ parentNode.getSnippetText());
		} else {
			// System.out.println(countAllNodesInTree + " vs " + totalNodeCount2
			// + "\n\n" + parentNode.getSnippetText());
		}
	}

	public static void validateTotalNodeCount(MyTreeNode root) {
		int subtreeNodeCount = root.countNodesInSubtree();
		if (subtreeNodeCount < MyTreeNode.totalNodeCount) {
			throw new RuntimeException("Nodes lost: ["
					+ MyTreeNode.totalNodeCount + ", " + subtreeNodeCount + "]");
		}
	}

	public static void resetValidationStats() {
		totalNodeCount = 0;
	}

}
