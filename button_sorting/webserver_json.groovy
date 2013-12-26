import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Joiner;
import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		@GET
		@Path("json")
		@Produces("application/json")
		public Response read(@QueryParam("filePath") String iFilePath)
				throws JSONException, IOException {
			// System.out.println("readFile() - begin");
			sort: {
				try {
					// Defragmenter.defragmentFile(iFilePath);
					// System.out.println("readFile() - sort successful");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			JSONObject json = new JSONObject();
			_1:{
				File f = new File(iFilePath);
				if (!f.exists()) {
					throw new RuntimeException();
				}
				// String contents = FileUtils.readFileToString(f);
				// json.put("entireFile", contents);
				MwkReader m = new MwkReader(iFilePath);
				try {
					JSONArray o = m.toJson();
					json.put("tree", o);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			// System.out.println(s);
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(json.toString()).type("application/json").build();
		}

		@POST
		@Path("persist")
		public Response persist(final String body) throws JSONException,
				IOException, URISyntaxException {
			System.out.println("persist() - begin");
			System.out.println(body);
			// Save the changes to the file
			save: {
				List<NameValuePair> params = URLEncodedUtils.parse(new URI(
						"http://www.fake.com/?" + body), "UTF-8");
				Map<String, String> m = new HashMap();
				for (NameValuePair param : params) {
					// System.out.println(param.getName() + " : "
					// + URLDecoder.decode(param.getValue(), "UTF-8"));
					m.put(param.getName(),
							URLDecoder.decode(param.getValue(), "UTF-8"));
				}
				FileUtils.write(new File(m.get("filePath")),
						m.get("newFileContents"));
				System.out.println("persist() - write successful");
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString())
					.type("application/json").build();
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(new URI(
				"http://localhost:9098/"), new ResourceConfig(
				HelloWorldResource.class));
	}

}
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Joiner;

public class MwkReader {
	private final String inputFilePath;
	private String _text;

	List<String> _lines;
	// ListIterator<String> _lineIter;
	int _startIndex = 0;
	int _endIndex = 0;

	public MwkReader(String iFilePath) throws IOException, JSONException {
		inputFilePath = iFilePath;
		JSONObject json = new JSONObject();
		File f = new File(iFilePath);
		if (!f.exists()) {
			throw new RuntimeException();
		}
		_2:{
			_lines = FileUtils.readLines(f);
			// String contents = FileUtils.readFileToString(f);
			// json.put("entireFile", _lines);
		}
	}

	@Deprecated
	// make this private
	public List<String> getNextSection() {
		if (_startIndex != _endIndex) {
			_startIndex = _endIndex;
		}
		List<String> aNextSection = new LinkedList<String>();

		// The first line is the heading and should not be disqualified for
		// being
		// equal to the heading level at which we will terminate
		String aCurrentLine = _lines.get(_endIndex++);
		aNextSection.add(aCurrentLine);

		aCurrentLine = _lines.get(_endIndex++);
		// 1) forward the end index to the end of this section
		// 2) accumulate the text to be displayed
		while (isWithinSection(aCurrentLine, 2)) {
			aNextSection.add(aCurrentLine);
			aCurrentLine = _lines.get(_endIndex++);
		}
		--_endIndex;
		if (_endIndex == _startIndex) {
			throw new IllegalAccessError("Developer Error");
		}
		return aNextSection;
	}

	private boolean isWithinSection(String iLine, final int iHeadingLevel) {
		int thisLinesLevel = 0;
		if (iLine == null || iLine.equals("")) {

		} else {
			for (int i = 1; i <= 6; i++) {
				String pattern = "^={" + i + "}[^=].*";
				if (iLine.matches(pattern)) {
					thisLinesLevel = i;
				}
			}
		}
		if (thisLinesLevel < 1) {
			return true;
		} else if (thisLinesLevel > iHeadingLevel) {
			return true;
		} else if (thisLinesLevel <= iHeadingLevel && thisLinesLevel > 0) {
			return false;// needs a new section
		} else {
			throw new IllegalAccessError("Developer error");
		}
	}

	private void rewindSection() {
		_lines = readFile(inputFilePath);
		_text = (Joiner.on("\n").join(locatePreviousSection()));
	}

	private static List<String> readFile(String inputFilePath) {
		List<String> theLines = null;
		try {
			theLines = FileUtils.readLines(new File(inputFilePath));

		} catch (IOException e) {
			e.printStackTrace();
		}
		return new CopyOnWriteArrayList<String>(theLines);
	}

	private List<String> locatePreviousSection() {

		if (_endIndex == _startIndex) {
			throw new IllegalAccessError("Developer Error");
		}

		if (_startIndex == 0) {
			// There is no previous section
			return _lines.subList(_startIndex, _endIndex);
		}

		_endIndex = _startIndex;
		if (_startIndex > 0) {
			--_startIndex;
		}
		String aCurrentLine = null;
		try {
			aCurrentLine = _lines.get(_startIndex);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println();
		}
		List<String> sectionBefore = new LinkedList<String>();

		while (isWithinSection(aCurrentLine, 2)) {
			sectionBefore.add(0, aCurrentLine);
			if (_startIndex > 0) {
				--_startIndex;
			} else {
				break;
			}
			try {
				aCurrentLine = _lines.get(_startIndex);
			} catch (ArrayIndexOutOfBoundsException e) {
				System.out.println();
			}
		}
		sectionBefore.add(0, aCurrentLine);

		if (_endIndex == _startIndex) {
			throw new IllegalAccessError("Developer Error");
		}
		return sectionBefore;
	}

	public JSONArray toJson() throws JSONException {
		int level = 1;
		JSONArray o = new JSONArray();
		addSectionsAtLevel(level, o, 0, _lines.size(), _lines);
		return o;
	}

	private static void addSectionsAtLevel(int level,
			JSONArray oSubObjectToFill, int startLineIdx, int endLineIdx,
			List<String> allLines) throws JSONException {
		ArrayList<String> al = new ArrayList<String>(allLines);
		// List<Pair<Integer, Integer>> startAndEndsAtLevelInRange =
		// getStartAndEndsAtLevelInRange();
		List<JSONObject> objectsAtLevel = getObjectsAtLevel(level,
				al.subList(startLineIdx, endLineIdx));
		for (JSONObject o : objectsAtLevel) {
			oSubObjectToFill.put(o);
		}
		System.out.println(oSubObjectToFill.toString(2));

	}

	private static List<JSONObject> getObjectsAtLevel(int level,
			List<String> subList) throws JSONException {
		String startingPattern = "^" + StringUtils.repeat('=', level) + "\\s.*";
		List<JSONObject> ret = new LinkedList<JSONObject>();
		// int start;
		// int end = 0;
		for (int start = 0; start < subList.size(); start++) {
			// ++end;
			String line = subList.get(start);
			if (!"= =".matches(startingPattern)) {
				throw new RuntimeException("wrong logic");
			}
			if (line.matches(startingPattern)) {
				System.out.println("match:" + line);
				int j = start + 1;
				while (j < subList.size()
						&& !subList.get(j).matches(startingPattern)) {
					++j;
				}
				// find ending line
				JSONObject js = convertStringRangeToJSONObject(
						subList.subList(start, j), level + 1);
				ret.add(js);
				start = j - 1;
			}

		}
		return ret;
	}

	private static JSONObject convertStringRangeToJSONObject(
			List<String> subList, int levelBelow) throws JSONException {
		// System.out.println("sublist: " + subList);
		JSONObject ret = new JSONObject();
		int start = 0;
		String heading = subList.get(start++) + "\n";
		if (heading.equals("")) {
			throw new RuntimeException("Incorrect assumption.");
		}
		ret.put("heading", heading);
		// first get free text
		String startingPattern = "^" + StringUtils.repeat('=', levelBelow) + "\\s.*";
		StringBuffer freeTextSb = new StringBuffer();
		JSONArray subsections = new JSONArray();
		for (; start < subList.size(); start++) {
			String str = subList.get(start);
			// System.out.println(str);
			if (str.matches(startingPattern)) {
				break;
			}
			if (str.startsWith("==== Value creation ====")) {
				throw new RuntimeException("This should never happen");
			}
			freeTextSb.append(str);
			freeTextSb.append("\n");

		}
		for (; start < subList.size(); start++) {

			if (!subList.get(start).startsWith("=")) {
				throw new RuntimeException("The first line should be a heading");
			}
			int end = start + 1;
			while (end < subList.size()
					&& !subList.get(end).matches(startingPattern)) {
				if (subList.get(end).startsWith(
						"=== Rohidekar brand - w/ value creation ===")) {
					System.out
							.println("check why this isn't including level 4");
				}
				++end;
			}
			int nextStartOrEnd = end;
			JSONObject innerObj = convertStringRangeToJSONObject(
					subList.subList(start, nextStartOrEnd), levelBelow + 1);
			subsections.put(innerObj);
			start = end - 1;
			if (end == subList.size()) {
				break;
			}
			String endingLine = subList.get(end);
			if (endingLine != null) {
				if (!endingLine.matches(startingPattern)) {
					throw new RuntimeException(
							"You can't have free text after the subsections have begun");
				}
			}

		}
		ret.put("freetext", freeTextSb.toString());
		ret.put("subsections", subsections);
		return ret;
	}
}
