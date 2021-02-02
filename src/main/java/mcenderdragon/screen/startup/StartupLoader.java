package mcenderdragon.screen.startup;


import com.google.gson.Gson;
import mcenderdragon.screen.main.ApplicationStarter;
import mcenderdragon.screen.main.MainScreenForWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class StartupLoader {

    private final String configFile;

    private final Gson gson;

    public StartupLoader(String configFile) {
        this.configFile = configFile;
        this.gson = new Gson();
    }

    public void parseConfig() {
        File f = new File(this.configFile);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            AppConfig[] apps = this.gson.fromJson(reader, AppConfig[].class);

            System.out.println("Found " + apps.length + " Apps");

            for(AppConfig app : apps) {
                if(app.isValid()) {
                    Runnable r = () -> {
                        System.out.println("Starting " + app.getName());
                        ApplicationStarter starter = new ApplicationStarter(
                                app.getCmd(), MainScreenForWindows.screen_port.getAsInt(),
                                app.getDirectory(), app.getName(), app.getGroup());
                        starter.start();
                    };
                    Thread t = new Thread(r);
                    t.start();
                }
                else {
                    System.out.format("The App %s from config is invalid and was skipped.", app.getName());
                }
            }
        }
        catch(FileNotFoundException ex) {
            System.out.format("No Startup Config found at %s.", this.configFile);
        }
    }
}
