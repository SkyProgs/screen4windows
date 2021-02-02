package mcenderdragon.screen.web;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import mcenderdragon.screen.main.MainScreenForWindows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class WebHelper {


    public static InputStream loadWebResourceAsStream(String path_b) {
        InputStream in_ = WebHelper.class.getResourceAsStream(path_b);

        if(in_ != null) {
            return in_;
        }

        String path = "./" + path_b;

        return WebHelper.class.getResourceAsStream(path);
    }

    public static byte[] gzip(byte[] data)throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length * 2);
        GZIPOutputStream out;
        out = new GZIPOutputStream(bout);
        out.write(data);
        out.flush();
        out.close();

        return bout.toByteArray();
    }

    public static void sendAnswer(HttpExchange http, int code, String answer) throws IOException {
        sendAnswer(http, code, answer.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendAnswer(HttpExchange http, int code, byte[] ant) throws IOException {
        if(http.getRequestHeaders().get("Accept-encoding").contains("gzip")) {
            ant = gzip(ant);
        }

        http.sendResponseHeaders(code, ant.length);
        http.getResponseBody().write(ant);
        http.getRequestBody().close();
        http.close();
    }

    public static void addResource(HttpsServer server, String path) throws IOException
    {
        InputStream in = MainScreenForWindows.class.getResourceAsStream("."+path);
        ByteArrayOutputStream bout = new ByteArrayOutputStream(in.available());
        byte[] b = new byte[1014];
        while(in.available() > 0)
        {
            int pos = in.read(b);
            bout.write(b, 0, pos);
        }
        byte[] resource = bout.toByteArray();
        b = gzip(resource);
        byte[] gzipResource = b.length > resource.length ? null : b;
        in.close();

        server.createContext(path, http ->
        {
            byte[] ant = resource;
            if(gzipResource!=null && http.getRequestHeaders().get("Accept-encoding").contains("gzip"))
            {
                ant = gzipResource;
            }
            http.sendResponseHeaders(200, ant.length);
            http.getResponseBody().write(ant);
            http.getRequestBody().close();
            http.close();
        });
    }

    public static HttpContext addResourceDirect(HttpsServer server, String path) {
        return addResourceDirect(server, path, "."+path);
    }

    public static HttpContext addResourceDirect(HttpsServer server, String url, String path_b) {
        return server.createContext(url, http ->
        {

            ByteArrayOutputStream bout = null;
            try
            {
                InputStream in = loadWebResourceAsStream(path_b);
                if(in == null)
                {
                    System.err.println("Could not load " + path_b);
                    String s = "Intern Server hickup no " + path_b;
                    sendAnswer(http, 500, s.getBytes(StandardCharsets.UTF_8));
                    return;
                }

                bout = new ByteArrayOutputStream(in.available());
                byte[] b = new byte[1014];
                while(in.available() > 0)
                {
                    int pos = in.read(b);
                    bout.write(b, 0, pos);
                }
                in.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            byte[] ant = bout.toByteArray();

            http.sendResponseHeaders(200, ant.length);
            http.getResponseBody().write(ant);
            http.getRequestBody().close();
            http.close();
        });
    }

    public static String escapeString(String line)
    {
        return line.chars().mapToObj(c -> {
                    if(c != '\\' && c != '"' && c >= 32 && c <= 126)//from Space to ~
                        return ((char) c) + "";
                    String s = Integer.toHexString(c);
                    while(s.length() < 4)
                        s = "0" + s;
                    return "\\u" + s;
                }).collect(Collectors.joining());
    }
}
