import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

			while ((line = br.readLine()) != null){

				
//				if (isHeading()) {
//					if (getHeadingLevel() == 3) {
//						// continue appending
//					} else if (getHeadingLevel() > 3) {
//						// continue appending
//					} else if (getHeadingLevel() < 3) {
//						// emit
//					} else {
//						throw new RuntimeException("Invalid case");
//					}
//				}
				
				
				System.out.println(line);
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
