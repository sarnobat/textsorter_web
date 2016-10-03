import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
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
		String fileContentsAfter = StringUtils.replace(fileContentsBefore, string, "", 1)  ;

		if (fileContentsBefore.length() != fileContentsAfter.length() + string.length()) {
			FileUtils.writeStringToFile(Paths.get("/sarnobat.garagebandbroken/Desktop/github-repositories/textsorter_web/pipes/before.txt").toFile(), fileContentsBefore);
			FileUtils.writeStringToFile(Paths.get("/sarnobat.garagebandbroken/Desktop/github-repositories/textsorter_web/pipes/after.txt").toFile(), fileContentsAfter);
			throw new RuntimeException("Full string did not get replaced. Wanted to remove " + string.length() + " chars but only removed " + (fileContentsBefore.length() - fileContentsAfter.length()));
		}
		FileUtils.writeStringToFile(dest.toFile(), fileContentsAfter);
		return fileContentsBefore.replace("\n","").length() - fileContentsAfter.replace("\n","").length();
	}

	private static int addToFile(String string, Path dest, String parentHeadingLevel2) throws IOException {
		String lines = FileUtils.readFileToString(dest.toFile());
		if (parentHeadingLevel2 == null || lines.indexOf("\n" +parentHeadingLevel2 +"\n") < 1) {
			
			// Insert it under level 1 heading "== =="
			
			Pattern p = Pattern.compile("(.*?)(==\\s(2\\s)?==\\n.*?)(=*?)");
			Matcher m = p.matcher(lines);
			if (m.find()) {
				String before = m.group(1);
				String level2Heading = m.group(2);
				String remainder = m.group(4);
				
				// TODO: if snippet contains a dollar, it doesn't get preserved after the replacefirst operation. Groovy's syntax doesn't mirror java's so I get an error when trying to replace.
				String snippetAdded = before + "" + level2Heading + escapeDollarSign(string);// + "\n";
				String out = unescapeDollarSign(m.replaceFirst(snippetAdded + remainder));
				
				FileUtils.writeStringToFile(dest.toFile(), out);
				
				return out.replace("\n", "").length() - lines.replace("\n", "").length();
			} else {
				throw new RuntimeException("Couldn't find a level 2 heading to attach snippet to.");
			}
		} else {
			if (lines.indexOf(parentHeadingLevel2) < 0) {
				throw new RuntimeException("TODO: Insert the heading");
			}
			String parentHeadingLevel2WithNewline = "\n" +parentHeadingLevel2 +"\n";
			String out = StringUtils.replaceOnce(lines, parentHeadingLevel2WithNewline, parentHeadingLevel2 + "\n" + string);
			FileUtils.writeStringToFile(dest.toFile(), out);
			return out.replace("\n", "").length() - lines.replace("\n", "").length();
		}
		
	}

	private static String escapeDollarSign(String input) {
	    StringBuilder b = new StringBuilder();

	    for (char c : input.toCharArray()) {
	        if (c == '\u0024'){ // dollar sign
	            b.append("__0024__");
			} else {
	            b.append(c);
	        }
	    }

	    return b.toString().replace("\\n", "__NEWLINE__").replace("\\", "__BACKSLASH__");
	}
	
	private static String unescapeDollarSign(String input) {
		// We can't use "$" directly in groovy so have to use this indirect way
		String dollarSign = new StringBuffer().append('\u0024').toString();
		return input.replace("__0024__", dollarSign)
				.replace("__NEWLINE__", "\\n")
				.replace("__BACKSLASH__", "\\")
				;
	}
}
