package mcenderdragon.screen.web.actions;

import com.sun.net.httpserver.HttpExchange;
import mcenderdragon.screen.main.ScreenService;
import mcenderdragon.screen.main.ScreenService.Client;
import mcenderdragon.screen.web.WebHelper;
import mcenderdragon.screen.web.WebAction;

import java.io.IOException;
import java.util.Map;

public class WebActionSendCommand extends WebAction {

    public WebActionSendCommand() {
        super("send_command");
    }

    @Override
    public void process(HttpExchange http, ScreenService screen, Map<String, String> entries) throws IOException {
        http.getResponseHeaders().add("Content-Type", "text/plain");

        int hex = Integer.decode("0x" + entries.get("server"));
        Client c = screen.getClients().get(hex);
        String command = entries.get("command");
        c.send(command);
        WebHelper.sendAnswer(http, 200, new byte[0]);
    }
}
