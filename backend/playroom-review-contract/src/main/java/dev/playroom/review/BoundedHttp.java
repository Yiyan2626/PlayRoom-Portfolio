package dev.playroom.review;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
/** 对整个响应体计时和限长，不只限制收到响应头前的等待；取消时释放本地连接资源。 */
public final class BoundedHttp {
    private BoundedHttp() { }
    public static HttpResponse<byte[]> send(HttpClient client,HttpRequest request,int maxBytes,Duration timeout) throws Exception {
        var future=client.sendAsync(request,info->new Limited(maxBytes));
        try { return future.get(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw interrupted; }
        finally { if(!future.isDone()) future.cancel(true); }
    }
    private static final class Limited implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final int max;
        private long count;
        private Flow.Subscription subscription;
        Limited(int max) {this.max=max;}
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
        public void onNext(List<ByteBuffer> items) {
            for(var item:items) count+=item.remaining();
            if(count>max){subscription.cancel();delegate.onError(new IllegalArgumentException("响应超过大小限制"));}
            else delegate.onNext(items);
        }
        public void onError(Throwable failure){delegate.onError(failure);}
        public void onComplete(){delegate.onComplete();}
    }
}
