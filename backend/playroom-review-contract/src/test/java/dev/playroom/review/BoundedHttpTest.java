package dev.playroom.review;
import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
class BoundedHttpTest {
    @Test void abortsOversizedResponseBody()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            e.sendResponseHeaders(200,4096);
            try{e.getResponseBody().write(new byte[4096]);}catch(Exception ignored){}finally{e.close();}
        });
        server.start();
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort())).GET().build();
            assertThrows(Exception.class,()->BoundedHttp.send(HttpClient.newHttpClient(),request,32,Duration.ofSeconds(2)));
        } finally {server.stop(0);}
    }
    @Test void timeoutIncludesBodyAfterHeadersHaveArrived()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            e.sendResponseHeaders(200,100);
            try {e.getResponseBody().write('a');e.getResponseBody().flush();Thread.sleep(1000);}
            catch(Exception ignored){}finally{e.close();}
        });
        server.start();
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort())).GET().build();
            long start=System.nanoTime();
            assertThrows(Exception.class,()->BoundedHttp.send(HttpClient.newHttpClient(),request,1000,Duration.ofMillis(80)));
            assertTrue((System.nanoTime()-start)<800_000_000L);
        } finally {server.stop(0);}
    }
}
