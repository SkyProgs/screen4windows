package mcenderdragon.screen.startup;

public class AppConfig {
    private String name;
    private String directory;
    private String cmd;
    private String group;

    AppConfig() { }

    public String getName() {
        return name == null || name.equals("") ? cmd : name;
    }

    public String getDirectory() {
        return directory;
    }

    public String getCmd() {
        return cmd;
    }

    public String getGroup() {
        if(group == null || group.equals("")) {
            return "default";
        }
        else {
            return group;
        }
    }

    public boolean isValid() {
        return !this.getCmd().equals("");
    }
}
