package mcenderdragon.screen.web;

import com.sun.net.httpserver.HttpExchange;
import mcenderdragon.screen.main.ScreenService;

import java.io.IOException;
import java.util.Map;

public abstract class WebAction {
    private final String name;

    public WebAction(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public abstract void process(HttpExchange http, ScreenService screen, Map<String,String> entries) throws IOException;
}
