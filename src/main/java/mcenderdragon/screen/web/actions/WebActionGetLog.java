package mcenderdragon.screen.web.actions;

import com.sun.net.httpserver.HttpExchange;
import mcenderdragon.screen.main.ScreenService;
import mcenderdragon.screen.main.ScreenService.Client;
import mcenderdragon.screen.web.WebHelper;
import mcenderdragon.screen.web.WebAction;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebActionGetLog extends WebAction {

    public WebActionGetLog() {
        super("get_log");
    }

    @Override
    public void process(HttpExchange http, ScreenService screen, Map<String,String> entries) throws IOException {
        http.getResponseHeaders().add("Content-Type", "application/json");

        int hex = Integer.decode("0x" + entries.get("server"));
        int lineStart = Integer.parseInt(entries.getOrDefault("line_start", "0"));
        Client c = screen.getClients().get(hex);

        if (c != null) {
            try {
                int[] actualLineStart = new int[]{c.getLineStart(lineStart)};
                Stream<String> lines = c.getLinesFrom(lineStart);
                String jsonArr = lines
                        .map(WebHelper::escapeString)
                        .map(s -> "{\"line\": " + (actualLineStart[0]++) + ", \"text\": \"" + s + "\"}")
                        .collect(Collectors.joining(", ", "[ ", " ]"));
                WebHelper.sendAnswer(http, 200, jsonArr);
            } catch (Exception e) {
                e.printStackTrace();
                if (e instanceof IOException)
                    throw e;
            }
        } else {

            String error = "{\"error\":\"Unknown Server \\\"" + entries.get("server") + "\\\"\"}";
            WebHelper.sendAnswer(http, 500, error);
        }
    }
}
