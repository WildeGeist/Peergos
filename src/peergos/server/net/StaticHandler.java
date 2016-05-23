package peergos.server.net;

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class StaticHandler implements HttpHandler
{
    private static Map<String, byte[]> data = new HashMap<>();
    private final boolean caching;
    private final String pathToRoot;

    public StaticHandler(String pathToRoot, boolean caching) throws IOException {
        this.caching = caching;
        this.pathToRoot = pathToRoot;
        for (File f: new File(pathToRoot).listFiles())
            processFile("", f);
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String path = httpExchange.getRequestURI().getPath();
        path = path.substring(1);
        path = path.replaceAll("//", "/");
        if (path.length() == 0)
            path = "index.html";

        byte[] res = caching ? data.get(path) : readResourceAndGzip(new File(pathToRoot + path).exists() ?
                new FileInputStream(pathToRoot + path)
                : ClassLoader.getSystemClassLoader().getResourceAsStream(pathToRoot + path));

        httpExchange.getResponseHeaders().set("Content-Encoding", "gzip");
        if (path.endsWith(".js"))
            httpExchange.getResponseHeaders().set("Content-Type", "text/javascript");
        else if (path.endsWith(".html"))
            httpExchange.getResponseHeaders().set("Content-Type", "text/html");
        else if (path.endsWith(".css"))
            httpExchange.getResponseHeaders().set("Content-Type", "text/css");
        else if (path.endsWith(".json"))
            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
        if (httpExchange.getRequestMethod().equals("HEAD")) {
            httpExchange.getResponseHeaders().set("Content-Length", ""+res.length);
            httpExchange.sendResponseHeaders(200, -1);
            return;
        }
        httpExchange.sendResponseHeaders(200, res.length);
        httpExchange.getResponseBody().write(res);
        httpExchange.getResponseBody().close();
    }

    private static void processFile(String path, File f) throws IOException {
        if (!f.isDirectory())
            data.put(path + f.getName(), readResourceAndGzip(new FileInputStream(f)));
        if (f.isDirectory())
            for (File sub: f.listFiles())
                processFile(path + f.getName() + "/", sub);
    }

    private static byte[] readResourceAndGzip(InputStream in) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        byte[] tmp = new byte[4096];
        int r;
        while ((r=in.read(tmp)) >= 0)
            gout.write(tmp, 0, r);
        gout.flush();
        gout.close();
        return bout.toByteArray();
    }

    private static List<String> getResources(String directory)
    {
        ClassLoader context = Thread.currentThread().getContextClassLoader();

        List<String> resources = new ArrayList<>();

        ClassLoader cl = StaticHandler.class.getClassLoader();
        if (!(cl instanceof URLClassLoader))
            throw new IllegalStateException();
        URL[] urls = ((URLClassLoader) cl).getURLs();

        int slash = directory.lastIndexOf("/");
        String dir = directory.substring(0, slash + 1);
        for (int i=0; i<urls.length; i++)
        {
            if (!urls[i].toString().endsWith(".jar"))
                continue;
            try
            {
                JarInputStream jarStream = new JarInputStream(urls[i].openStream());
                while (true)
                {
                    ZipEntry entry = jarStream.getNextEntry();
                    if (entry == null)
                        break;
                    if (entry.isDirectory())
                        continue;

                    String name = entry.getName();
                    slash = name.lastIndexOf("/");
                    String thisDir = "";
                    if (slash >= 0)
                        thisDir = name.substring(0, slash + 1);

                    if (!thisDir.startsWith(dir))
                        continue;
                    resources.add(name);
                }

                jarStream.close();
            }
            catch (IOException e) { e.printStackTrace();}
        }
        InputStream stream = context.getResourceAsStream(directory);
        try
        {
            if (stream != null)
            {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[1024];
                try (Reader r = new InputStreamReader(stream))
                {
                    while (true)
                    {
                        int length = r.read(buffer);
                        if (length < 0)
                        {
                            break;
                        }
                        sb.append(buffer, 0, length);
                    }
                }

                for (String s : sb.toString().split("\n"))
                {
                    if (s.length() > 0 && context.getResource(directory + s) != null)
                    {
                        resources.add(s);
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return resources;
    }
}
