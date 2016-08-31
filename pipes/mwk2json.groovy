import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.json.JSONObject;

import com.google.common.base.Joiner;

/**
 * Emits heading 3 snippets as json objects, otherwise emits verbatim.
 */
public class Mwk2Json {

	public static void main(String[] args) {
		BufferedReader br = null;
		try {

			br = new BufferedReader(new InputStreamReader(System.in));

			String level3snippet = "";

			String line = "";
			boolean insideLevel3Snippet = false;
			while ((line = br.readLine()) != null) {

				if (isHeading(line)) {
					if (getHeadingLevel(line) == 3) {
						if (insideLevel3Snippet) {
							// emit
							JSONObject snippet3Json = createEmitJson(level3snippet);
							System.out.println(snippet3Json);
							level3snippet = "";
							insideLevel3Snippet = false; // Pointless but just for completeness
						} 
						// start appending
						level3snippet += line + "\n";
						insideLevel3Snippet = true;
					} else if (getHeadingLevel(line) > 3) {
						// continue appending
						level3snippet += line + "\n";
						if (!insideLevel3Snippet) {
							throw new RuntimeException("We must be inside a level 3 snippet");
						}
					} else if (getHeadingLevel(line) < 3) {

						// emit
						JSONObject snippet3Json = createEmitJson(level3snippet);
						System.out.println(snippet3Json);
						level3snippet = "";
						insideLevel3Snippet = false;
					} else {
						throw new RuntimeException("Invalid case");
					}
				} else {
					insideLevel3Snippet = isInsideLevel3Heading(line, insideLevel3Snippet);
					if (insideLevel3Snippet) {
						// continue appending
						level3snippet += line + "\n";
					} else {
						// emit
						System.out.println(line);
						// there shouldn't be anything accumulated
						if (level3snippet.length() > 0) {
							throw new RuntimeException(
									"nothing should be accumulated if we are outside a level 3 snippet: >>>>"
											+ level3snippet + "<<<<");
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static JSONObject createEmitJson(String level3snippet) {
		String[] snippet3Lines = level3snippet.split("\\n");
		JSONObject snippet3Json = new JSONObject();
		snippet3Json.put("heading", snippet3Lines[0]);
		snippet3Json
				.put("body",
						Joiner.on('\n').join(
								Arrays.copyOfRange(snippet3Lines, 1,
										snippet3Lines.length)));
		return snippet3Json;
	}

	private static boolean isInsideLevel3Heading(String line, boolean insideLevel3Snippet) {
		boolean insideLevel3SnippetRet;
		if (isHeading(line)) {
			if (getHeadingLevel(line) == 3) {
				insideLevel3SnippetRet = true;
			} else if (getHeadingLevel(line) > 3) {
				insideLevel3SnippetRet = true;
			} else if (getHeadingLevel(line) < 3) {
				insideLevel3SnippetRet = false;
			} else {
				throw new RuntimeException("Invalid case");
			}
		} else {
			insideLevel3SnippetRet = insideLevel3Snippet;
		}
		return insideLevel3SnippetRet;
	}

	private static boolean isHeading(String line) {
		return line.startsWith("=");
	}

	private static int getHeadingLevel(String line) {
		int level = 0;
		int i = 0;
		while (line.charAt(i) == '=') {
			++i;
			++level;
		}

		if (level == 0) {
			throw new RuntimeException("Not a heading: " + line);
		} else {
			return i;
		}
	}

}
