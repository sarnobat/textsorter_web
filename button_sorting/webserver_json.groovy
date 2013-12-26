import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ButtonSorterServer {
	public static void main(String[] args) throws URISyntaxException {
		JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4455/"), new ResourceConfig(
						HelloWorldResource.class));
	}

	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		@GET
		@Path("json")
		@Produces("application/json")
		public Response read(@QueryParam("filePath") String iFilePath)
				throws JSONException, IOException {
			try {
				JSONObject mwkFileAsJson = new JSONObject();
				File mwkFile = new File(iFilePath);
				if (!mwkFile.exists()) {
					throw new RuntimeException();
				}
				JSONArray o = toJson(iFilePath);
				mwkFileAsJson.put("tree", o);

				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(mwkFileAsJson.toString())
						.type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		@SuppressWarnings("unused")
		@POST
		@Path("persist")
		@Deprecated
		// I don't think we're using this
		public Response persist(final String body) throws JSONException,
				IOException, URISyntaxException {
			System.out.println("persist() - begin");
			System.out.println(body);
			// Save the changes to the file
			save: {
				List<NameValuePair> params = URLEncodedUtils.parse(new URI(
						"http://www.fake.com/?" + body), "UTF-8");
				Map<String, String> m = new HashMap<String, String>();
				for (NameValuePair param : params) {
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

		@POST
		@Path("move")
		public Response move(@QueryParam("filePath") String iFilePath,
				@QueryParam("id") final String iIdOfObjectToMove,
				@QueryParam("destId") final String iIdOfLocationToMoveTo)
				throws JSONException, IOException, URISyntaxException {

			try {
				JSONArray topLevelArray = toJson(iFilePath);

				JSONObject destination = findSnippetById(iIdOfLocationToMoveTo,
						topLevelArray);
				if (destination == null) {
					throw new RuntimeException("couldn't find "
							+ iIdOfLocationToMoveTo);
				}
				JSONObject snippetOriginalParent = findParentOfSnippetById(
						iIdOfObjectToMove, topLevelArray);
				if (snippetOriginalParent == null) {
					throw new RuntimeException("couldn't find "
							+ iIdOfObjectToMove);
				}
				JSONObject snippetToMove = removeObject(iIdOfObjectToMove,
						topLevelArray, snippetOriginalParent);
				if (snippetToMove == null) {
					throw new RuntimeException("Couldn't find snippet "
							+ iIdOfObjectToMove);
				}
				if (!destination.getString("id").equals(iIdOfLocationToMoveTo)) {
					System.out.println("Wrong location");
					throw new RuntimeException("Wrong destination");
				}
				destination.getJSONArray("subsections").put(snippetToMove);

				try {
					String string = asString(topLevelArray).toString();
					FileUtils.writeStringToFile(new File(iFilePath), string);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(topLevelArray.toString())
						.type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;

		}

		// TODO: why do we need the top level array?
		private JSONObject removeObject(final String iIdOfObjectToRemove,
				JSONArray topLevelArray, JSONObject snippetOriginalParent) {
			JSONObject rDesiredSnippet = null;
			if (snippetOriginalParent == null) {
				throw new IllegalArgumentException();
			}
			JSONArray subsectionsOfParent = snippetOriginalParent
					.getJSONArray("subsections");
			for (int i = 0; i < subsectionsOfParent.length(); i++) {
				JSONObject aSubsection = subsectionsOfParent.getJSONObject(i);
				if (aSubsection.getString("id")
						.equals(iIdOfObjectToRemove)) {
					rDesiredSnippet = (JSONObject) subsectionsOfParent.remove(i);
				}
			}
			if (rDesiredSnippet == null) {
				throw new RuntimeException("Did not find snippet in parent");
			}
			return rDesiredSnippet;
		}

		@Deprecated
		private JSONObject notWorking(final String iIdOfObjectToRemove,
				JSONArray topLevelArray, JSONObject snippetOriginalParent) {
			System.out.println("snippetOriginalParent - "
					+ snippetOriginalParent.getString("id"));
			System.out.println("IdOfObjectToRemove - " + iIdOfObjectToRemove);
			System.out.println();
			JSONObject rDesiredSnippet = null;
			JSONArray subsectionsArray = (JSONArray) snippetOriginalParent
					.get("subsections");
			for (int i = 0; i < topLevelArray.length(); i++) {
				if (rDesiredSnippet != null) {
					throw new RuntimeException(
							"Bug. We already found the snippet. We don't want to keep searching");
				}
				if (topLevelArray.get(i) != null) {
					for (int j = 0; j < subsectionsArray.length(); j++) {
						if (rDesiredSnippet != null) {
							throw new RuntimeException(
									"Bug. We already found the snippet. We don't want to keep searching");
						}
						JSONObject subtreeRootJsonObject = subsectionsArray
								.getJSONObject(j);
						JSONObject aParentSnippet = findSnippetById(
								iIdOfObjectToRemove, subtreeRootJsonObject);
						if (aParentSnippet != null) {

							rDesiredSnippet = aParentSnippet;
							JSONArray allParentSubsections = snippetOriginalParent
									.getJSONArray("subsections");
							boolean foundParent = false;
							for (int k = 0; i < allParentSubsections.length(); i++) {
								System.out.println(allParentSubsections
										.getJSONObject(k).getString("id"));
								if (iIdOfObjectToRemove
										.equals(allParentSubsections
												.getJSONObject(k).getString(
														"id"))) {
									allParentSubsections.remove(k);
									foundParent = true;
									break;
								}
							}
							if (foundParent) {
								break;
							} else {
								throw new RuntimeException(
										"Found parent but did not remove snippet.");
							}
						} else {
							throw new RuntimeException(
									"Parent Snippet not found");
						}
					}
					break;
				}
			}
			return rDesiredSnippet;
		}

		private JSONObject findParentOfSnippetById(String iIdOfObjectToMove,
				JSONArray a) {
			for (int i = 0; i < a.length(); i++) {
				JSONObject jsonObject = a.getJSONObject(i);
				JSONObject p = findParentOfSnippetById(iIdOfObjectToMove,
						jsonObject);
				if (p != null) {
					return p;
				}
			}
			throw new RuntimeException("Couldn't find parent of "
					+ iIdOfObjectToMove);
		}

		private JSONObject findParentOfSnippetById(String iIdOfObjectToMove,
				JSONObject jsonObject) {
			JSONArray a = jsonObject.getJSONArray("subsections");
			for (int i = 0; i < a.length(); i++) {
				if (iIdOfObjectToMove
						.equals(a.getJSONObject(i).getString("id"))) {
					return jsonObject;
				} else {
					JSONObject parentCandidate = findParentOfSnippetById(
							iIdOfObjectToMove, a.getJSONObject(i));
					if (parentCandidate != null) {
						return parentCandidate;
					}
				}
				// JSONObject jsonObject2 = a.getJSONObject(i);
				// JSONObject o = findSnippetById(iIdOfObjectToMove,
				// jsonObject2);
				// if (o != null) {
				// return jsonObject2;
				// }
			}
			return null;
		}

		private JSONObject findSnippetById(String iIdOfObjectToMove, JSONArray a) {
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = findSnippetById(iIdOfObjectToMove,
						a.getJSONObject(i));
				if (o != null) {
					return o;
				}
			}
			return null;
		}

		private JSONObject findSnippetById(String iIdOfObjectToMove,
				JSONObject jsonObject) {
			String string2 = jsonObject.getString("id");
			if (iIdOfObjectToMove.equals(string2)) {
				return jsonObject;
			}
			JSONArray arr = (JSONArray) jsonObject.getJSONArray("subsections");

			for (int j = 0; j < arr.length(); j++) {
				JSONObject jsonObject2 = arr.getJSONObject(j);
				JSONObject o = findSnippetById(iIdOfObjectToMove, jsonObject2);
				if (o != null) {
					return o;
				}
			}
			return null;
		}
	}

	public static JSONArray toJson(String iFilePath) throws JSONException,
			IOException {
		List<String> _lines;
		File f = new File(iFilePath);
		if (!f.exists()) {
			throw new RuntimeException();
		}
		_lines = FileUtils.readLines(f);
		int level = 1;
		JSONArray o = new JSONArray();
		addSectionsAtLevel(level, o, 0, _lines.size(), _lines);
		return o;
	}

	private static void addSectionsAtLevel(int level,
			JSONArray oSubObjectToFill, int startLineIdx, int endLineIdx,
			List<String> allLines) throws JSONException {
		ArrayList<String> al = new ArrayList<String>(allLines);
		List<JSONObject> objectsAtLevel = getObjectsAtLevel(level,
				al.subList(startLineIdx, endLineIdx));
		for (JSONObject o : objectsAtLevel) {
			oSubObjectToFill.put(o);
		}

	}

	private static List<JSONObject> getObjectsAtLevel(int level,
			List<String> subList) throws JSONException {
		String startingPattern = "^" + StringUtils.repeat('=', level) + "\\s.*";
		List<JSONObject> ret = new LinkedList<JSONObject>();
		for (int start = 0; start < subList.size(); start++) {
			String line = subList.get(start);
			if (!"= =".matches(startingPattern)) {
				throw new RuntimeException("wrong logic");
			}
			if (line.matches(startingPattern)) {
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
		JSONObject ret = new JSONObject();
		int start = 0;
		String heading = subList.get(start++) + "\n";
		if (heading.equals("")) {
			throw new RuntimeException("Incorrect assumption.");
		}
		ret.put("heading", heading);
		// first get free text
		String equals = StringUtils.repeat('=', levelBelow);
		String startingPattern = "^" + equals + "\\s.*";
		StringBuffer freeTextSb = new StringBuffer();
		JSONArray subsections = new JSONArray();
		for (; start < subList.size(); start++) {
			String str = subList.get(start);
			if (str.matches(startingPattern)) {
				break;
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
		String string = freeTextSb.toString();
		ret.put("freetext", string);
		ret.put("subsections", subsections);
		ret.put("id", DigestUtils.md5Hex(heading + string));

		return ret;
	}

	public static StringBuffer asString(JSONArray topLevelArray) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < topLevelArray.length(); i++) {
			JSONObject subtree = topLevelArray.getJSONObject(i);
			sb.append(subtree.getString("heading"));
			sb.append(subtree.getString("freetext"));
			JSONArray subsections = subtree.getJSONArray("subsections");
			StringBuffer sb1 = asString(subsections);
			sb.append(sb1);
		}
		return sb;
	}

}
