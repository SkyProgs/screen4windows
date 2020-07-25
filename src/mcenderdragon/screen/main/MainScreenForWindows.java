package mcenderdragon.screen.main;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class MainScreenForWindows 
{
	public static BooleanSupplier need_authentication = () -> Boolean.getBoolean("screen.webservice.need_authentication");
	public static IntSupplier screen_port = () -> Integer.getInteger("screen.port", 9153);
	public static Supplier<String> web_adress = () -> System.getProperty("screen.webservice.adress", "localhost");
	public static IntSupplier web_port = () -> Integer.getInteger("screen.webservice.port", 8087);
	public static Supplier<String> auth_file = () -> System.getProperty("screen.webservice.auth_file", "users.txt");
	public static Function<String,String> run_name = a -> System.getProperty("application.name", a);
	
	public static Supplier<String> keystore_file = () -> System.getProperty("screen.webservice.ssl.file", "./testkey.jks");
	public static Supplier<String> keystore_password = () -> System.getProperty("screen.webservice.ssl.password", "password");
	
	
	public static void main(String[] args) 
	{
		
		List<CommandEntry> entries = new ArrayList(2);
		
		entries.add(new CommandEntry("run", "run a programm with the speicified args, like \"run ping google\" will execute \"ping google\" and connect it to the screen server.") 
		{
			@Override
			public void command(String[] args, int readPos) 
			{
				String command = args[readPos];
				readPos++;
				for(;readPos<args.length;readPos++)
				{
					command += " " + args[readPos];
				}
				ApplicationStarter starter = new ApplicationStarter(command, MainScreenForWindows.screen_port.getAsInt());
				starter.start();
			}
		});
		entries.add(new CommandEntry("screen", "run the screen service, see readme for more details and options")
		{
			@Override
			public void command(String[] args, int readPos) 
			{
				try 
				{
					ScreenService screen = new ScreenService(screen_port.getAsInt());
				
					
					WebService web = new WebService(web_port.getAsInt(), screen);
					web.start();
					
					screen.start();
				}
				catch (Exception e) 
				{
					e.printStackTrace();
				}
			}
		});
		
		
		
		if(args.length==0)
		{
			System.out.println("run help for more info.");
			return;
		}
		else if(args.length >= 1)
		{
			if("help".equals(args[0]))
			{
				System.out.println("Read the Readme at: https://github.com/mcenderdragon/screen4windows/blob/master/Readme.md");
				System.out.println("Commands are: ");
				entries.forEach(System.out::println);
			}
			else
			{
				for(CommandEntry e : entries)
				{
					if(e.command.equals(args[0]))
					{
						e.command(args, 1);
						return;
					}
				}
				System.out.println("unknown command, use help for more info");
			}
		}
		
//		try 
//		{
//			ScreenService screen = new ScreenService(screen_port.getAsInt());
//		
//			
//			WebService web = new WebService(web_port.getAsInt(), screen);
//			web.start();
//			
//			screen.start();
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//		}
		
		
		
		
		
	}
	
	public static abstract class CommandEntry
	{
		public final String command;
		public final String description;
		
		public CommandEntry(String command, String description) 
		{
			super();
			this.command = command;
			this.description = description;
		}

		public abstract void command(String[] args, int readPos);
		
		@Override
		public String toString() 
		{
			return " " + command+": " + description;
		}
	}
	
	
}
