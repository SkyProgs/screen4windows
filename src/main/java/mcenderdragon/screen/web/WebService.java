package mcenderdragon.screen.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import mcenderdragon.screen.main.MainScreenForWindows;
import mcenderdragon.screen.main.ScreenService;
import mcenderdragon.screen.web.actions.WebActionDeleteInactive;
import mcenderdragon.screen.web.actions.WebActionGetLog;
import mcenderdragon.screen.web.actions.WebActionGetClients;
import mcenderdragon.screen.web.actions.WebActionSendCommand;

public class WebService
{
	public static boolean DEBUG_WEBSERVER = false;

	private HttpsServer httpsServer;
	private int webSidePort;
	
	private ScreenService screen;
	
	private MessageDigest digest;
	private Properties users;

	private WebAction[] actions;
	
	public WebService(int webSidePort, ScreenService screen) throws NoSuchAlgorithmException, FileNotFoundException, IOException 
	{
		this.webSidePort = webSidePort;
		this.screen = screen;

		this.actions = new WebAction[] {
				new WebActionGetClients(),
				new WebActionGetLog(),
				new WebActionSendCommand(),
				new WebActionDeleteInactive()
		};

		if(MainScreenForWindows.need_authentication.getAsBoolean())
		{
			digest = MessageDigest.getInstance("SHA-256");
			Properties props = new Properties();
			props.load(new FileInputStream(new File("./", MainScreenForWindows.auth_file.get())));
			users = props;
		}
	}
	
	public void start()
	{

		try 
		{
			System.out.println("Starting Website at " + webSidePort);
			
			// Set up the socket address
			InetSocketAddress address = new InetSocketAddress(MainScreenForWindows.web_adress.get(), webSidePort);

			// Initialise the HTTPS server
			httpsServer = HttpsServer.create(address, 0);
			SSLContext sslContext = SSLContext.getInstance("TLS");

			// Initialise the keystore
			char[] password = MainScreenForWindows.keystore_password.get().toCharArray();
			KeyStore ks = KeyStore.getInstance("JKS");
			
			File keys = new File(MainScreenForWindows.keystore_file.get());
			if(!keys.exists())
			{
				System.err.println("KeyStore file (for HTTPS) '"+keys.toString()+"' not found");
				System.out.println("Please run \"<your java path>/bin/keytool.exe -genkeypair -keyalg RSA -alias self_signed -keypass <password> -keystore lig.keystore -storepass <password>\"");
				return;
			}
			
			FileInputStream fis = new FileInputStream(keys);
			ks.load(fis, password);

			// Set up the key manager factory
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, password);

			// Set up the trust manager factory
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);

			// Set up the HTTPS context and parameters
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) 
			{
				public void configure(HttpsParameters params) 
				{
					if(DEBUG_WEBSERVER) {
						System.out.println(new Date().toString());
						System.out.println(Arrays.toString(params.getProtocols()));
					}
					try {
						// Initialise the SSL context
						SSLContext c = SSLContext.getDefault();
						SSLEngine engine = c.createSSLEngine();
						if(MainScreenForWindows.need_authentication.getAsBoolean())
						{
							params.setNeedClientAuth(true);
							params.setWantClientAuth(true);
						}
						params.setCipherSuites(engine.getEnabledCipherSuites());
						params.setProtocols(engine.getEnabledProtocols());

						// Get the default parameters
						SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
						params.setSSLParameters(defaultSSLParameters);
					} catch (Exception ex) {
						System.err.println("Failed to create HTTPS port");
					}
				}
			});
			
			BasicAuthenticator auth = null;
			if(MainScreenForWindows.need_authentication.getAsBoolean())
			{
				auth = new BasicAuthenticator("GET")
				{
	
					@Override
					public boolean checkCredentials(String username, String password) 
					{
						String hashed = sha256(password);
						String table = users.getProperty(username, "");
						boolean b = table.equals(hashed);
						if(!b)
							System.out.println(username + " password missmatch given " + hashed + " versus expected " + table);
						return b;
					}
				};
			}
			WebHelper.addResourceDirect(httpsServer, "/index.html", "index.html").setAuthenticator(auth);
			WebHelper.addResourceDirect(httpsServer, "/icon.ico", "icon.ico").setAuthenticator(auth);
			WebHelper.addResourceDirect(httpsServer, "/core.css", "core.css").setAuthenticator(auth);
			WebHelper.addResourceDirect(httpsServer, "/script.js", "script.js").setAuthenticator(auth);
			WebHelper.addResourceDirect(httpsServer, "/ansi-up.js", "ansi-up.js").setAuthenticator(auth);
			httpsServer.createContext("/data", this::onData).setAuthenticator(auth);
			httpsServer.createContext("/", WebService::onIndex).setAuthenticator(auth);

	
			httpsServer.setExecutor(null); // creates a default executor
			System.out.println("Starting Server");
			httpsServer.start();
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		
		
	}
	
	public String sha256(String raw)
	{
		digest.reset();
		byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
		StringBuilder builder = new StringBuilder(bytes.length);
		for(byte b : bytes)
		{
			int i = 0xFF & b;
			String s = Integer.toHexString(i);
			if(s.length() < 2)
				builder.append("0");
			builder.append(s);
		}
		return builder.toString();
	}
	
	public static void onIndex(HttpExchange http) throws IOException
	{
		if(DEBUG_WEBSERVER) {
			System.out.println(http.getRequestMethod());
			System.out.println(http.getProtocol());
			System.out.println(Arrays.toString(http.getHttpContext().getAttributes().entrySet().toArray()));
			System.out.println(Arrays.toString(http.getRequestHeaders().entrySet().toArray()));
			System.out.println(http.getRequestURI());
		}

		if(http.getRequestURI().toString().equals("/"))
		{
			http.getResponseHeaders().add("Location", "/index.html");
			http.sendResponseHeaders(302, -1);
			http.getRequestBody().close();
			http.close();
			return;
		}
		
		String requestData = getRequestData(http, 1024 * 100);
		if(DEBUG_WEBSERVER)
			System.out.println("Request: " + requestData);
		
		byte[] antw = "Go away I dont know what I am doing".getBytes(StandardCharsets.UTF_8);
		http.getRequestHeaders().add("Content-Type", "text/plain");
		
		WebHelper.sendAnswer(http, 200, antw);
	}
	
	public static String getRequestData(HttpExchange http, int maxDataLength) throws IOException
	{
		StringBuilder builder = new StringBuilder(maxDataLength);
		InputStream in = http.getRequestBody();
		for(int i=0;i<maxDataLength;i++)
		{
			int data = in.read();
			if(data != -1)
			{
				builder.append((char)data);
			}
			else
			{
				break;
			}
		}
		String requestData = builder.toString();
		builder = null;
		return requestData;
	}

	public void onData(HttpExchange http) throws IOException
	{
		if(http.getRequestMethod().equals("POST"))
		{
			Map<String,String> entries = new HashMap<>();
			Arrays.stream(getRequestData(http, 1024*10).split("&")).map(s -> s.split("=", 2)).forEach(array -> entries.put(array[0], array[1]));
			
			String action = entries.get("action");
			
			if(action == null)
			{
				byte[] err = "no Action spezified".getBytes(StandardCharsets.UTF_8);
				WebHelper.sendAnswer(http, 400, err);
				return;
			}

			for (WebAction webAction : this.actions) {
				if(webAction.getName().equalsIgnoreCase(action)) {
					webAction.process(http, screen, entries);
					return;
				}
			}

			byte[] err = ("unknown action \"" + action + "\"").getBytes(StandardCharsets.UTF_8);
			WebHelper.sendAnswer(http, 400, err);
			
		}
		else
		{
			http.sendResponseHeaders(403, -1);//forbidden
			http.getRequestBody().close();
			http.close();
		}
	}

}
