import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
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

				JSONObject json = new JSONObject(line);

				// First add it to the destination
				addToFile(json.getString("heading") + "\n" + json.getString("body"), dest);

				// Then remove it from the source
				// removeFromFile(json, src);
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

	private static void addToFile(String string, Path dest) throws IOException {
		String lines = FileUtils.readFileToString(dest.toFile());
		Pattern p = Pattern.compile("(.*?)(==\\s(2\\s)?==\\n.*?)(=*?)");
		Matcher m = p.matcher(lines);
		if (m.find()) {
			String before = m.group(1);
			String level2Heading = m.group(2);
			String remainder = m.group(4);
			String out = m.replaceFirst(before + "" + level2Heading + "\n" + string + "\n"
					+ remainder);
			FileUtils.writeStringToFile(dest.toFile(), out);
		} else {
			throw new RuntimeException("Couldn't find a level 2 heading to attach snippet to.");
		}
		//lines.replaceFirst("(==\\s(2\\s)?==)", "$1\n" + string);
	}
}
