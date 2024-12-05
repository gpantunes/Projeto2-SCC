package tukano.impl.rest;

import java.net.URI;
import java.util.logging.Logger;
import java.util.*;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import jakarta.ws.rs.core.Application;
import tukano.auth.Authentication;
import tukano.auth.ControlResource;
import tukano.auth.cookies.RequestCookiesCleanupFilter;
import tukano.auth.cookies.RequestCookiesFilter;
import tukano.impl.Token;
import utils.Args;
import utils.IP;

public class TukanoRestServer extends Application {

	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "https://%s/rest";

	public static final int PORT = 8080;

	private static String appName = "scc-backend-70231-70735.azurewebsites.net";
	//private static String appName = "127.0.0.1:8080/tukano";

	public static String serverURI;

	//flags para definir o que se vai utilizar
	public static final boolean cacheOn = false;
	public static final boolean sqlOn = false;

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public TukanoRestServer() {
		serverURI = String.format(SERVER_BASE_URI, appName);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
		
		resources.add(ControlResource.class);
		resources.add(RequestCookiesFilter.class);
     	resources.add(RequestCookiesCleanupFilter.class);
        resources.add(Authentication.class);

		Token.setSecret(Args.valueOf("-secret", "123"));
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	protected void start() throws Exception {

		ResourceConfig config = new ResourceConfig();

		config.registerClasses(resources);

		JdkHttpServerFactory.createHttpServer(URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);

		Log.warning(String.format("Tukano Server ready @ %s\n", serverURI));
	}

	public static void main(String[] args) throws Exception {
		Args.use(args);

		//Token.setSecret("123");
		// Props.load( Args.valueOf("-props", "").split(","));

		new TukanoRestServer().start();
	}
}