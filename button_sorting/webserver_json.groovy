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
			System.out.println("readFile() - begin");
			sort: {
				try {
					Defragmenter.defragmentFile(iFilePath);
					System.out.println("readFile() - sort successful");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			JSONObject json = new JSONObject();
			File f = new File(iFilePath);
			if (!f.exists()) {
				throw new RuntimeException();
			}
			// String contents = FileUtils.readFileToString(f);
			// json.put("entireFile", contents);
			MwkReader m = new MwkReader(iFilePath);
			for (int i = 0; i < 10; i++) {
				String s = Joiner.on("\n").join(m.getNextSection());
				json.put("section" + i, s);
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;
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
		_lines = FileUtils.readLines(f);
		String contents = FileUtils.readFileToString(f);
		json.put("entireFile", _lines);
	}

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
	public void rewindSection() {
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
}
