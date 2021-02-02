package mcenderdragon.screen.main;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ScreenService implements Closeable
{
	public boolean shouldRun = true;
	
	private int port;
	
	private final Object lock = new Object();
	
	private List<Client> newClients = new ArrayList<>(5);
	private final Map<Integer, Client> clients = Collections.synchronizedMap(new HashMap<>());
	
	private final Thread server;
	private final ServerSocket serverSocket;
	
	public ScreenService(int port) throws IOException
	{
		System.out.println("Opening Screen Server at " + port);
		this.port = port;
		
		server = new Thread(() -> {
			while(shouldRun)
			{
				try {
					awaitNewConnection();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		serverSocket = new ServerSocket(port);
	}
	
	
	public Client awaitNewConnection() throws IOException
	{
		Client c = new Client(serverSocket.accept());
		synchronized (lock) 
		{
			newClients.add(c);
		}
		
		return c;
	}
	
	public void startServer()
	{
		server.start();
	}
	
	public void processNewClients()
	{
		List<Client> l;
		synchronized (lock) 
		{
			l = newClients;
			newClients = new ArrayList<>(5);
		}
		for(Client c : l)
		{
			c.start();
			clients.put(c.hashCode(), c);
		}
	}
	
	public void start()
	{
		startServer();
		
		while(shouldRun)
		{
			processNewClients();
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void close() throws IOException 
	{
		shouldRun = false;
		serverSocket.close();
		Consumer<Client> closer = t -> 
		{
			try {
				t.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
		newClients.forEach(closer);
		clients.values().forEach(closer);
	}
	
	public static class Client implements Closeable
	{
		private final Socket connection;
		private int startLine = 0;
		private final List<String> lines;
		private final BufferedReader in;
		
		public String name;
		public String group;
	
		private Thread thread;
		
		public Client(Socket con) throws IOException
		{
			System.out.println("New client " +hashCode());
			con.setKeepAlive(true);
			this.connection = con;
			in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
			lines = Collections.synchronizedList(new ArrayList<String>(3001));
			
		}
		
		private synchronized void addLine(String s)
		{
			lines.add(s);
			System.out.println("Client " + hashCode() + ": " + s);
			if(lines.size()>3000)
			{
				startLine++;
				lines.remove(0);
			}
		}
		
		public synchronized String getLine(int line)
		{
			line -= startLine;
			if(line<0)
				return null;
			else
			{
				return lines.get(line);
			}
		}
		
		public int getStartLine()
		{
			return startLine;
		}
		
		public boolean isAlive()
		{
			return connection!=null && connection.isConnected() && !connection.isClosed();
		}
		
		public void send(String s) throws IOException
		{
			if(isAlive())
			{
				if(!s.endsWith("\r"))
					s = s + "\r";
				
				addLine("\u001b[40m\u001b[32m > " + s + "\u001b[0m");//black background, gren font, >text, reset
				
				OutputStream out = connection.getOutputStream();
				out.write(s.getBytes(StandardCharsets.UTF_8));
				out.flush();
			}
		}
		
		public void readLines() throws IOException
		{
			try
			{
				String line = in.readLine();
				if(line==null)
				{
					connection.close();
					return;
				}
					
				addLine(line);
			}
			catch(IOException e)
			{
				connection.close();
			}
		}

		@Override
		public void close() throws IOException 
		{
			connection.close();
			in.close();
		}
		
		public void start()
		{
			try 
			{
				name = in.readLine();
				group = in.readLine();
				System.out.println("Client " + hashCode() + " name is " + name + " and group is " + group);
			}
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			thread = new Thread(() -> {
				while(isAlive())
				{
					try
					{
						long time = System.currentTimeMillis();
						readLines();
						time = System.currentTimeMillis() - time;

						if(time > 10)
						{
							Thread.sleep(250);
						}


					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				}
				System.out.println("Client " + Client.this.hashCode() + " finished");
			}, name);
			thread.start();
		}

		public synchronized int getLineStart(int lineStart) 
		{
			return Math.max(lineStart, this.startLine);
		}
		
		public synchronized Stream<String> getLinesFrom(int lineStart) 
		{
			int actualStart = Math.max(lineStart, this.startLine);
			int listStart = actualStart - startLine;
			return IntStream.range(listStart, lines.size()).mapToObj(lines::get);
		}
	}

	public boolean deleteClient(int hex) throws IOException {
		Client c = this.clients.get(hex);
		if(c != null) {
			if(c.isAlive()) {
				return false;
			}
			c.close();
			this.clients.remove(hex);
		}
		return true;
	}

	public Map<Integer, Client> getClients() 
	{
		return Collections.unmodifiableMap(this.clients);
	}
}
