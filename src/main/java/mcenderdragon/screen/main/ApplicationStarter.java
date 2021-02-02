package mcenderdragon.screen.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

public class ApplicationStarter 
{
	private Process process;
	private final String start_command;
	private final String directory;
	private boolean started;
	private boolean running;
	
	private final int server_port;
	
	private Socket server;
	private final String name;
	private final String group;

	public ApplicationStarter(String command, int serverPort) {
		this(command, serverPort, null, MainScreenForWindows.run_name.apply(command), "default");
	}

	public ApplicationStarter(String command, int serverPort, String directory, String name, String group)
	{
		this.start_command = command;
		this.server_port = serverPort;
		this.directory = directory;
		this.name = name+"\r\n";
		this.group = group+"\r\n";
	}
	
	public synchronized Process getOrCreateProcess() throws IOException 
	{
		if (this.process != null) 
		{
			return this.process;
		}
		ProcessBuilder builder = new ProcessBuilder(this.start_command.split(" "));
		if(this.directory != null)
			builder.directory(new File(this.directory));
		builder.environment().put("MSYSTEM", "MINGW-managment");//this is to disable jline support; if jline is active it will crash later on and we cant communicate with the server/program
		builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
		builder.redirectError(ProcessBuilder.Redirect.PIPE);
		builder.redirectInput(ProcessBuilder.Redirect.PIPE);
		System.out.println("[INFO] Now starting \"" + this.start_command + "\"");
		this.process = builder.start();
		this.started = true;
		this.running = true;
		return this.process;
	}
	
	public boolean isStarted() 
	{
		return this.started;
	}

	public synchronized boolean isRunning() throws IOException 
	{
		if (this.started) 
		{
			Process p = this.getOrCreateProcess();
			this.running = p.isAlive();
			return this.running;
		}
		return false;
	}
	
	public synchronized Socket connectToScreenServer() throws IOException
	{
		if(server == null || server.isClosed() || !server.isConnected())
		{
			server = new Socket();
			server.setKeepAlive(true);
			System.out.println("Try connecting to localhost:" + server_port);
			server.connect(new InetSocketAddress("localhost", server_port), 5*1000);
			return server;
		}
		else
		{
			return server;
		}
	}
	
	public synchronized boolean isServerAvailable()
	{
		return server != null && !server.isClosed() && server.isConnected();
	}
	
	public static void transferData(Callable<InputStream> suppIn, Callable<OutputStream> suppOut, BooleanSupplier isRunning, boolean isError) throws Exception
	{
		byte[] redBg = "\u001b[41m".getBytes(StandardCharsets.UTF_8);
		byte[] reset = "\u001b[0m".getBytes(StandardCharsets.UTF_8);
		
		InputStream in = suppIn.call();
		byte[] data = new byte[10240]; 
		OutputStream out = suppOut.call();
		while(isRunning.getAsBoolean())
		{
			if(in.available() > 0)
			{
				int p = in.read(data, 0, Math.min(data.length, in.available()));
				if(isError)
				{
					out.write(redBg);
				}
				out.write(data, 0, p);
				if(isError)
				{
					out.write(reset);
				}
				out.flush();
			}
			else
			{
				Thread.sleep(1000);
				out.write(in.read());
			}
		}
		
		while(in.available() > 0)
		{
			int p = in.read(data, 0, Math.min(data.length, in.available()));
			if(isError)
			{
				out.write(redBg);
			}
			out.write(data, 0, p);
			if(isError)
			{
				out.write(reset);
			}
			out.flush();
		}
	}
	
	public void writeOutputToServer() 
	{
		try {
			Process p = getOrCreateProcess();
			transferData(p::getInputStream, server::getOutputStream, p::isAlive, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void writeErrorToServer() 
	{
		try {
			Process p = getOrCreateProcess();
			transferData(p::getErrorStream, server::getOutputStream, p::isAlive, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void readFromServer()
	{
		try {
			Process p = getOrCreateProcess();
			transferData(server::getInputStream, p::getOutputStream, p::isAlive, false);
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	public void start()
	{
		try 
		{
			Socket s = connectToScreenServer();
			
			getOrCreateProcess();
			
			s.getOutputStream().write(name.getBytes(StandardCharsets.UTF_8));
			s.getOutputStream().write(group.getBytes(StandardCharsets.UTF_8));;
			s.getOutputStream().write(0);
			
			Runnable[] runnables = new Runnable[3];
			runnables[0] = this::writeOutputToServer;
			runnables[1] = this::writeErrorToServer;
			runnables[2] = this::readFromServer;
			
			String[] threadNames = new String[] {"output to server", "error to server", "input from server"};
			
			Thread[] workers = new Thread[3];

			
			while(isRunning())
			{
				for(int i=0;i<workers.length;i++)
				{
					Thread t = workers[i];
					if(t==null)
					{
						t = new Thread(runnables[i], threadNames[i]);
						workers[i] = t;
						System.out.println("Starting worker " + t.getName());
						t.start();
					}
					if(!t.isAlive() && isRunning())
					{
						System.err.println("Worker " + t.getName() + " died!");
						System.err.println("Closing Socket");
						if(s!=null)
							s.close();
						
						workers[i] = null;
						t = null;
							
					}
					if(!isServerAvailable())
					{
						System.out.println("Lost connection to screen server, reconnecting...");
						try
						{
							s = connectToScreenServer();
							if(s!=null)
							{
								System.out.println("Reconnected");
								s.getOutputStream().write(name.getBytes(StandardCharsets.UTF_8));
								s.getOutputStream().write(0);
							}
						}
						catch(ConnectException e)
						{
							System.out.println(e.toString());
						}
					}
				}
				
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			for(Thread t : workers)
			{
				try {
					t.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			System.out.println("Process Finished with exitcode " + getOrCreateProcess().exitValue());
			
			
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		if(process!=null)
			process.destroyForcibly();
		
		if(server!=null && !server.isClosed())
		{
			try {
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
