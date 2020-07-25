package mcenderdragon.screen.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

public class ApplicationStarter 
{
	private Process process;
	private String start_command;
	private boolean started;
	private boolean running;
	
	private int server_port;
	
	private Socket server;
	private String name;
	
	public ApplicationStarter(String command, int serverPort) 
	{
		this.start_command = command;
		this.server_port = serverPort;
		name = MainScreenForWindows.run_name.apply(start_command + "\r\n");
	}
	
	public synchronized Process getOrCreateProcess() throws IOException 
	{
		if (this.process != null) 
		{
			return this.process;
		}
		ProcessBuilder builder = new ProcessBuilder(this.start_command.split(" "));
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
	
	public synchronized Socket connectToScreenServer() throws UnknownHostException, IOException
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
	
	public static void traferData(Callable<InputStream> suppIn, Callable<OutputStream> suppOut, BooleanSupplier isRunning, boolean isError) throws Exception
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
				Thread.sleep(100);
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
			traferData(p::getInputStream, server::getOutputStream, p::isAlive, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void writeErrorToServer() 
	{
		try {
			Process p = getOrCreateProcess();
			traferData(p::getErrorStream, server::getOutputStream, p::isAlive, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void readFromServer()
	{
		try {
			Process p = getOrCreateProcess();
			traferData(server::getInputStream, p::getOutputStream, p::isAlive, false);
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
			name = null;
			s.getOutputStream().write(0);
			
			Runnable[] runnables = new Runnable[3];
			Thread output = new Thread(runnables[0] = this::writeOutputToServer, "output to server");
			Thread error = new Thread(runnables[1] = this::writeErrorToServer, "error to server");
			Thread input = new Thread(runnables[2] = this::readFromServer, "input from server");
			Thread[] workers = new Thread[] {output, error, input};
			for(Thread t : workers)
			{
				t.start();
			}
			
			while(isRunning())
			{
				for(int i=0;i<workers.length;i++)
				{
					Thread t = workers[i];
					if(!t.isAlive() && isRunning())
					{
						System.err.println("Worker " + t.getName() + " died!");
					}
					if(!isServerAvailable())
					{
						System.out.println("Lost connection to screen server, reconnecting...");
						connectToScreenServer();
					}
					else if(!t.isAlive() && isRunning())
					{
						Thread restart = new Thread(runnables[i], t.getName());
						workers[i] = restart;
						restart.start();
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
