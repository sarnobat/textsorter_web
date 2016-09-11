import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

				// First add it to the destination
				int linesAdded = addToFile(json.getString("heading") 
						+ "\n"
						+ json.getString("body"), dest);

				// Then remove it from the source
				int linesRemoved =  removeFromFile(json.getString("heading") + "\n" + json.getString("body"), src);
				// The extra 1 is for the additional newline
				if (linesAdded != linesRemoved + 1) {
					System.err.println("JsonMoveMwk.main() " + json);
// 					throw new RuntimeException("linesAdded != linesRemoved: " + linesAdded + " vs " + linesRemoved);
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
			throw new RuntimeException("Full string did not get replaced. Wanted to remove "
					+ string.length() + " chars but only removed "
					+ (fileContentsBefore.length() - fileContentsAfter.length()));
		}
		FileUtils.writeStringToFile(dest.toFile(), fileContentsAfter);
		return fileContentsBefore.length() - fileContentsAfter.length();
	}

	private static int addToFile(String string, Path dest) throws IOException {
		String lines = FileUtils.readFileToString(dest.toFile());
		Pattern p = Pattern.compile("(.*?)(==\\s(2\\s)?==\\n.*?)(=*?)");
		Matcher m = p.matcher(lines);
		if (m.find()) {
//			System.err.println(string);
			String before = m.group(1);
//			System.err.println(before);
			String level2Heading = m.group(2);
//			System.err.println(level2Heading);
			String remainder = m.group(4);
//			System.err.println(remainder);
			String out = m.replaceFirst(before + "" + level2Heading + string + "\n" + remainder);
			FileUtils.writeStringToFile(dest.toFile(), out);
			return out.length() - lines.length();
		} else {
			throw new RuntimeException("Couldn't find a level 2 heading to attach snippet to.");
		}
	}
}
