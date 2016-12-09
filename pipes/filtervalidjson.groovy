import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONObject;

/**
 * Discards invalidjson
 */
public class FilterValidJson {

	public static void main(String[] args) {

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line = "";
			while ((line = br.readLine()) != null) {
				try {
					new JSONObject(line);
					System.out.println(line);
				} catch (org.json.JSONException e) {
					// invalid, do not emit
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
}