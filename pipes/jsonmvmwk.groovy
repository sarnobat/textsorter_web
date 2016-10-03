import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * moves a json snippet, passed via stdin, from one mwk file to another
 */ 
public class JsonMoveMwk {

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: groovy jsonmvmwk.groovy  src.mwk dest.mwk");
			System.exit(-1);
		}
		Path src = Paths.get(args[0]);
		Path dest = Paths.get(args[1]);

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line = "";
			while ((line = br.readLine()) != null) {
				JSONObject json;
				try {
					json = new JSONObject(line);
				} catch (org.json.JSONException e) {
					System.err.println("line was:\n\t" + line);
					e.printStackTrace();
					return;
				}				
				
				// TODO: if this json object exists more than once in the file,
				// do not move it. You'll end up moving the wrong one. This is
				// difficut to implement so for now just don't move anything
				// that has no body.
				if (json.getString("body").trim().length() == 0) {
					continue;
				}

				if (json.getString("heading").length() > 0) {
					// First add it to the destination
					int charsAdded = addToFile(
							json.getString("heading") + "\n" + json.getString("body"), dest,
							json.getString("parent"));

					// Then remove it from the source
					int charsRemoved = removeFromFile(json.getString("heading") + "\n" + json.getString("body"), src);
					// The extra 1 is for the additional newline
					if (Math.abs(charsAdded - charsRemoved) > 1) {
//						System.err.println("JsonMoveMwk.main() ADDED:\t"
//								+ json.getString("heading") + "\n" + json.getString("body"));
//						System.err.println("JsonMoveMwk.main() REMOVED\t: "
//								+ json.getString("heading") + "\n" + json.getString("body"));
						throw new RuntimeException("linesAdded != linesRemoved: " + charsAdded
								+ " vs " + charsRemoved);
					}
					// TODO: count the number of snippets added and removed
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

	private static int removeFromFile(String string, Path dest) throws IOException {
		String fileContentsBefore = FileUtils.readFileToString(dest.toFile());
		//System.err.println("JsonMoveMwk.removeFromFile() :"+string);
		String fileContentsAfter = StringUtils.replace(fileContentsBefore, string, "", 1)  ;

		if (fileContentsBefore.length() != fileContentsAfter.length() + string.length()) {
			FileUtils.writeStringToFile(Paths.get("/sarnobat.garagebandbroken/Desktop/github-repositories/textsorter_web/pipes/before.txt").toFile(), fileContentsBefore);
			FileUtils.writeStringToFile(Paths.get("/sarnobat.garagebandbroken/Desktop/github-repositories/textsorter_web/pipes/after.txt").toFile(), fileContentsAfter);
			throw new RuntimeException("Full string did not get replaced. Wanted to remove " + string.length() + " chars but only removed " + (fileContentsBefore.length() - fileContentsAfter.length()));
		}
		FileUtils.writeStringToFile(dest.toFile(), fileContentsAfter);
		return fileContentsBefore.length() - fileContentsAfter.length();
	}

	private static int addToFile(String string, Path dest, String parentHeadingLevel2) throws IOException {
		String lines = FileUtils.readFileToString(dest.toFile());
		if (parentHeadingLevel2 == null || lines.indexOf("\n" +parentHeadingLevel2 +"\n") < 1) {
			System.err.println("JsonMoveMwk.addToFile() - no level 2 heading");
			Pattern p = Pattern.compile("(.*?)(==\\s(2\\s)?==\\n.*?)(=*?)");
			Matcher m = p.matcher(lines);
			if (m.find()) {
				String before = m.group(1);
				String level2Heading = m.group(2);
				String remainder = m.group(4);
				
				// TODO: if snippet contains a dollar, it doesn't get preserved after the replacefirst operation. Groovy's syntax doesn't mirror java's so I get an error when trying to replace.
				String out = unescapeDollarSign(m.replaceFirst(before + "" + level2Heading +escapeDollarSign(string) + "\n" + remainder));
				
//				System.err.println("JsonMoveMwk.addToFile() :"+ string);
				FileUtils.writeStringToFile(dest.toFile(), out);
				System.err.println("JsonMoveMwk.addToFile() - out.length() = " + out.length());
				System.err.println("JsonMoveMwk.addToFile() - lines.length() = " + lines.length());
				return out.length() - lines.length();
			} else {
				throw new RuntimeException("Couldn't find a level 2 heading to attach snippet to.");
			}
		} else {
			System.err.println("JsonMoveMwk.addToFile() - has a level 2 heading");
			if (lines.indexOf(parentHeadingLevel2) < 0) {
				throw new RuntimeException("TODO: Insert the heading");
			}
			String parentHeadingLevel2WithNewline = "\n" +parentHeadingLevel2 +"\n";
			String out = StringUtils.replaceOnce(lines, parentHeadingLevel2WithNewline, parentHeadingLevel2 + "\n" + string);
			FileUtils.writeStringToFile(dest.toFile(), out);
			System.err.println("JsonMoveMwk.addToFile() - out.length() = " + out.length());
			System.err.println("JsonMoveMwk.addToFile() - lines.length() = " + lines.length());
			return out.length() - lines.length();
		}
		
	}

	private static String escapeDollarSign(String input) {
	    StringBuilder b = new StringBuilder();

	    for (char c : input.toCharArray()) {
	        if (c == '\u0024'){
	            b.append("__0024__");//"\\u").append(String.format("%04X", (int) c));
	        }
	        else {
	            b.append(c);
	        }
	    }

	    return b.toString();
	}
	
	private static String unescapeDollarSign(String input) {
		String s = new StringBuffer().append('\u0024').toString();
		return input.replace("__0024__", s);
	}
}
