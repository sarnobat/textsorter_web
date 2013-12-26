import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

public class TextSorterWebServer {
	private static final String DIR_WITH_MWK_FILES = "/Users/sarnobat/Desktop/git-repo/mwk";

	@Path("textsorter")
	public static class HelloWorldResource { // Must be public

		// -----------------------------------------------------------------------------
		// Key bindings
		// -----------------------------------------------------------------------------
		@GET
		@Path("json")
		@Produces("application/json")
		public Response read(@QueryParam("filePath") String iFilePath)
				throws JSONException, IOException {
			try {
				JSONObject categoriesJson = new JSONObject();
				File mwkFile = new File(iFilePath);
				if (!mwkFile.exists()) {
					throw new RuntimeException();
				}
				JSONObject o = getCategories(DIR_WITH_MWK_FILES);
				categoriesJson.put("categories", o);

				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(categoriesJson.toString())
						.type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		// TODO: make this private
		public static JSONObject getCategories(String dirWithMwkFiles)
				throws IOException {
			JSONObject o = new JSONObject();
			java.nio.file.Path dirPath = Paths.get(dirWithMwkFiles);
			DirectoryStream<java.nio.file.Path> stream = Files
					.newDirectoryStream(dirPath);
			for (java.nio.file.Path anMwkFilePath : stream) {
				if (anMwkFilePath.getFileName().toString().endsWith(".mwk")) {
					o.put(anMwkFilePath.getFileName().toString().replace(".mwk", ""), anMwkFilePath
							.toString());
				}
			}
			return o;
		}
	}

	public static void main(String[] args) throws URISyntaxException,
			IOException {
		System.out.println(HelloWorldResource.getCategories(DIR_WITH_MWK_FILES)
				.toString(2));

		JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4451/"), new ResourceConfig(
						HelloWorldResource.class));
	}
}

