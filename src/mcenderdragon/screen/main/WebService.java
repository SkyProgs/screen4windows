package mcenderdragon.screen.main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import mcenderdragon.screen.main.ScreenService.Client;

public class WebService 
{
	private HttpsServer httpsServer;
	private int webSidePort;
	
	private ScreenService screen;
	
	private MessageDigest digest;
	private Properties users;
	
	public WebService(int webSidePort, ScreenService screen) throws NoSuchAlgorithmException, FileNotFoundException, IOException 
	{
		this.webSidePort = webSidePort;
		this.screen = screen;
		
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
			// Set up the socket address
			InetSocketAddress address = new InetSocketAddress("localhost", webSidePort);

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
					System.out.println(new Date().toString());
					System.out.println(Arrays.toString(params.getProtocols()));
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
						return users.getProperty(username, "").equals(hashed);
					}
				};
			}
			addResourceDirect(httpsServer, "/index.html").setAuthenticator(auth);
			addResourceDirect(httpsServer, "/icon.ico").setAuthenticator(auth);
			addResourceDirect(httpsServer, "/core.css").setAuthenticator(auth);
			addResourceDirect(httpsServer, "/script.js").setAuthenticator(auth);
			addResourceDirect(httpsServer, "/ansi-up.js").setAuthenticator(auth);
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
		System.out.println(http.getRequestMethod());
		System.out.println(http.getProtocol());
		System.out.println(Arrays.toString(http.getHttpContext().getAttributes().entrySet().toArray()));
		System.out.println(Arrays.toString(http.getRequestHeaders().entrySet().toArray()));
		System.out.println(http.getRequestURI());
		
		if(http.getRequestURI().toString().equals("/"))
		{
			http.getResponseHeaders().add("Location", "/index.html");
			http.sendResponseHeaders(302, -1);
			http.getRequestBody().close();
			http.close();
			return;
		}
		
		String requestData = getRquestData(http, 1024 * 100);
		System.out.println("Request: " + requestData);
		
		byte[] antw = "Go away I dont know what I am doing".getBytes(StandardCharsets.UTF_8);
		http.getRequestHeaders().add("Content-Type", "text/plain");
		
		sendAnswer(http, 200, antw);
	}
	
	public static String getRquestData(HttpExchange http, int maxDataLength) throws IOException
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
	
	public static void sendAnswer(HttpExchange http, int code, byte[] ant) throws IOException
	{
		if(http.getRequestHeaders().get("Accept-encoding").contains("gzip"))
		{
			ant = gzip(ant);
		}
		
		http.sendResponseHeaders(code, ant.length);
		http.getResponseBody().write(ant);
		http.getRequestBody().close();
		http.close();
	}
	
	public static byte[] gzip(byte[] data)throws IOException
	{
		ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length * 2);
		GZIPOutputStream out;
		out = new GZIPOutputStream(bout);
		out.write(data);
		out.flush();
		out.close();
		
		return bout.toByteArray();
	}
	
	public static void addResource(HttpsServer server, String path) throws IOException
	{
		InputStream in = MainScreenForWindows.class.getResourceAsStream("."+path);
		ByteArrayOutputStream bout = new ByteArrayOutputStream(in.available());
		byte[] b = new byte[1014];
		while(in.available() > 0)
		{
			int pos = in.read(b);
			bout.write(b, 0, pos);
		}
		byte[] resource = bout.toByteArray();
		b = gzip(resource);
		byte[] gzipResource = b.length > resource.length ? null : b;
		in.close();
		
		server.createContext(path, http -> 
		{
			byte[] ant = resource;
			if(gzipResource!=null && http.getRequestHeaders().get("Accept-encoding").contains("gzip"))
			{
				ant = gzipResource;
			}
			http.sendResponseHeaders(200, ant.length);
			http.getResponseBody().write(ant);
			http.getRequestBody().close();
			http.close();
		});
	}
	
	public static HttpContext addResourceDirect(HttpsServer server, String path) throws IOException
	{
		return addResourceDirect(server, path, "."+path);
	}
	public static HttpContext addResourceDirect(HttpsServer server, String url, String path) throws IOException
	{
		return server.createContext(url, http -> 
		{
			ByteArrayOutputStream bout = null;
			try
			{
				InputStream in = MainScreenForWindows.class.getResourceAsStream(path);
				bout = new ByteArrayOutputStream(in.available());
				byte[] b = new byte[1014];
				while(in.available() > 0)
				{
					int pos = in.read(b);
					bout.write(b, 0, pos);
				}
				in.close();
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			
			byte[] ant = bout.toByteArray();
			
			http.sendResponseHeaders(200, ant.length);
			http.getResponseBody().write(ant);
			http.getRequestBody().close();
			http.close();
		});
	}
	
	public void onData(HttpExchange http) throws IOException
	{
		if(http.getRequestMethod().equals("POST"))
		{
			Map<String,String> entries = new HashMap<>();
			Arrays.stream(getRquestData(http, 1024*10).split("&")).map(s -> s.split("=", 2)).forEach(array -> entries.put(array[0], array[1]));
			
			String action = entries.get("action");
			
			if(action==null)
			{
				byte[] err = "no Action spezified".getBytes(StandardCharsets.UTF_8);
				sendAnswer(http, 500, err);
				return;
			}
			
			if(action.equals("get_servers"))
			{
				
				http.getResponseHeaders().add("Content-Type", "application/json");
				StringBuilder builder = new StringBuilder();
				builder.append('[');
				boolean first = true;
				for(Entry<Integer, Client> e : screen.getClients().entrySet())
				{
					if(!first)
					{
						builder.append(',');
					}
					
					builder.append("{ \"id\": \"");
					builder.append(Integer.toHexString(e.getKey()));
					builder.append("\", \"name\": \"");
					builder.append(e.getValue().name);
					builder.append("\", \"running\": ");
					builder.append(e.getValue().isAlive());
					builder.append('}');
					first = false;
				}
				builder.append(']');
				sendAnswer(http, 200, builder.toString().getBytes(StandardCharsets.UTF_8));
			}
			else if(action.equals("get_log"))
			{
				http.getResponseHeaders().add("Content-Type", "application/json");
			
				int hex = Integer.decode("0x" + entries.get("server"));
				int lineStart = Integer.valueOf(entries.getOrDefault("line_start", "0"));
				Client c = screen.getClients().get(hex);
				
				if(c!=null)
				{
					try
					{
						int[] actualLineStart = new int[] {c.getLineStart(lineStart)};
						Stream<String> lines = c.getLinesFrom(lineStart);
						String jsonArr = lines
								.map(WebService::escapeString)
								.map(s -> "{\"line\": " + (actualLineStart[0]++) + ", \"text\": \"" + s + "\"}")
								.collect(Collectors.joining(", ", "[ ", " ]"));
						sendAnswer(http, 200, jsonArr.getBytes(StandardCharsets.UTF_8));
					}
					catch(Exception e)
					{
						e.printStackTrace();
						if(e instanceof IOException)
							throw e;
					}
				}
				else
				{
					byte[] err = ("unknown server \""+entries.get("server")+"\"").getBytes(StandardCharsets.UTF_8);
					sendAnswer(http, 500, err);
					return;
				}
			}
			else if(action.equals("send_command"))
			{
				http.getResponseHeaders().add("Content-Type", "text/plain");
				
				int hex = Integer.decode("0x" + entries.get("server"));
				Client c = screen.getClients().get(hex);
				String command = entries.get("command");
				c.send(command);
				sendAnswer(http, 200, new byte[0]);
			}
			else
			{
				byte[] err = ("unknown action \""+action+"\"").getBytes(StandardCharsets.UTF_8);
				sendAnswer(http, 500, err);
				return;
			}
			
		}
		else
		{
			http.sendResponseHeaders(403, -1);//forbidden
			http.getRequestBody().close();
			http.close();
			return;
		}
	}
	
	private static String escapeString(String line)
	{
		return line.chars().mapToObj(c -> {
					if(c != '\\' && c != '"' && c >= 32 && c <= 126)//from Space to ~
						return ((char) c) + "";
					String s = Integer.toHexString(c);
					while(s.length() < 4)
						s = "0" + s;
					return "\\u" + s;
				}).collect(Collectors.joining());
	}
}
