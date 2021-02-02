package mcenderdragon.screen.web.actions;

import com.sun.net.httpserver.HttpExchange;
import mcenderdragon.screen.main.ScreenService;
import mcenderdragon.screen.web.WebHelper;
import mcenderdragon.screen.web.WebAction;

import java.io.IOException;
import java.util.Map;

public class WebActionGetClients extends WebAction {

    public WebActionGetClients() {
        super("get_clients");
    }

    @Override
    public void process(HttpExchange http, ScreenService screen, Map<String,String> entries) throws IOException {
        http.getResponseHeaders().add("Content-Type", "application/json");
        String msg = buildServerInfoString(screen);
        WebHelper.sendAnswer(http, 200, msg);
    }

    public static String buildServerInfoString(ScreenService screen) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        boolean first = true;
        for (Map.Entry<Integer, ScreenService.Client> e : screen.getClients().entrySet()) {
            if (!first) {
                builder.append(',');
            }

            builder.append("{ \"id\": \"");
            builder.append(Integer.toHexString(e.getKey()));
            builder.append("\", \"name\": \"");
            builder.append(e.getValue().name);
            builder.append("\", \"group\": \"");
            builder.append(e.getValue().group);
            builder.append("\", \"running\": ");
            builder.append(e.getValue().isAlive());
            builder.append('}');
            first = false;
        }
        builder.append(']');

        return builder.toString();
    }
}
