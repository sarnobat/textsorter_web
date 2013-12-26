import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		@GET
		@Path("json")
		@Produces("application/json")
		public Response json(@QueryParam("filePath") String iFilePath) throws JSONException,
				IOException {
			System.out.println("readFile() - begin");
			JSONObject json = new JSONObject();
			File f = new File(iFilePath);
			if (!f.exists()) {
				throw new RuntimeException();
			}
			String contents = FileUtils.readFileToString(f);
			json.put("entireFile", contents);

			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		@POST
		@Path("persist")
		public Response persist(final String body) throws JSONException, IOException,
				URISyntaxException {
			System.out.println("persist() - begin");
			System.out.println(body);
			{
				List<NameValuePair> params = URLEncodedUtils.parse(new URI("http://www.fake.com/?"
						+ body), "UTF-8");
				for (NameValuePair param : params) {
					System.out.println(param.getName() + " : "
							+ URLDecoder.decode(param.getValue(), "UTF-8"));
				}
			}
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString()).type("application/json").build();
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:9099/"), new ResourceConfig(HelloWorldResource.class));
	}
}
