import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.*;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
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
//		@Consumes("application/json")
//		@Produces("application/json")
		public Response persist(
		final String body
		//, @QueryParam("filePath") String iFilePath
		) throws JSONException,
				IOException {
			//System.out.println(iFilePath);
			System.out.println("persist() - begin");
			System.out.println(body);
		//FileUtils.write
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(new JSONObject().toString())
					.type("application/json").build();
		}
	}
	


	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:9099/"), new ResourceConfig(HelloWorldResource.class));
	}
}
