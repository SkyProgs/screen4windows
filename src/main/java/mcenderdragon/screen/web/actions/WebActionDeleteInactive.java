package mcenderdragon.screen.web.actions;

import com.sun.net.httpserver.HttpExchange;
import mcenderdragon.screen.main.ScreenService;
import mcenderdragon.screen.web.WebHelper;
import mcenderdragon.screen.web.WebAction;

import java.io.IOException;
import java.util.Map;

public class WebActionDeleteInactive extends WebAction {

    public WebActionDeleteInactive() {
        super("delete_inactive");
    }

    @Override
    public void process(HttpExchange http, ScreenService screen, Map<String, String> entries) throws IOException {
        http.getResponseHeaders().add("Content-Type", "application/json");

        int hex = Integer.decode("0x" + entries.get("server"));

        if(!screen.deleteClient(hex)) {
            String error = ("{\"error\":\"The Application is still running and cannot be deleted.\"}");
            WebHelper.sendAnswer(http, 403, error);
            return;
        }

        String activeServers = WebActionGetClients.buildServerInfoString(screen);

        WebHelper.sendAnswer(http, 200, activeServers);
    }
}
