package io.github.bckfnn.callback;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

public interface Callback<T> extends Handler<AsyncResult<T>> {
    
    /**
     * finish the callback with an success value.
     * @param value the value to send back.
     */
    default void ok(T value) {
        handle(Future.succeededFuture(value));
    }

    /**
     * Finish the callback with a null success value.
     */
    default void ok() {
        handle(Future.succeededFuture());
    }

    /**
     * Finish the callback with a failure condition.
     * @param error the error to send back.
     */
    default void fail(Throwable error) {
        handle(Future.failedFuture(error));
    }
    
    /**
     * Return a new callback where the handler is invoked on success and a error condition is passed to this callback.
     * @param handler the handler to call on success.
     * @param <R> type of the returned success value.
     * @return a new callback.
     * <h2>Example</h2>
     * <pre>
     *     public void sub(Callback&lt;Long&gt; cb) {
     *         subsub(cb.call(value -&gt; {
     *             cb.ok(value + 1);
     *         }
     *     }
     *
     *     public void main() {
     *         sub((v, e) -&gt; {
     *             System.out.println(v + " " + e);
     *         }
     *     }
     *
     *     public void subsub(Callback&lt;Long&gt; cb) {
     *         cb.ok(42);
     *     }
     * </pre>
     */
    default <R> Callback<R> call(Consumer<R> handler) {
        return result -> {
            if (result.failed()) {
                fail(result.cause());
            } else {
                try {
                    handler.accept(result.result());
                } catch (Throwable exc) {
                    fail(exc);
                }
            }
        };
    }
    
    /**
     * Convert a Handler<AsyncResult<T>> into a callback
     * @param <R>
     * @param handler
     * @return a callback
     */
    public static <R> Callback<R> callback(Handler<AsyncResult<R>> handler) {
        return result -> handler.handle(result);
    }
    
    /**
     * Iterate sequentially over the elements in the list. For each element call the elmHandler.
     * @param <E> type of elements in the list
     * @param list the list of data
     * @param elmHandler the elmHandler to call for each element.
     *
     * The elmHandler take two arguments, the actual element and a Callback that must be used to signal success or failure
     * of handling the element. Only when the callback's ok() method is called will the next element be iterated.
     *
     * Calling fail() of the callback will stop the iteration.
     */
    default <E> void forEach(List<E> list, BiConsumer<E, Callback<Void>> elmHandler) {
        forEach(list, elmHandler, this::ok);
    }

    /**
     * Iterate sequentially over the elements in the list. For each element call the elmHandler.
     * @param <E> type of elements in the list
     * @param list the list of data
     * @param elmHandler the elmHandler to call for each element.
     * @param done the done handler that is invoked when all elements have been iterated successfully.
     *
     * The elmHandler take two arguments, the actual element and a Callback that must be used to signal success or failure
     * of handling the element. Only when the callback's ok() method is called will the next element be iterated.
     *
     * Calling fail() of the callback will stop the iteration and the done handler will not be called.
     */
    default <E> void forEach(List<E> list, BiConsumer<E, Callback<Void>> elmHandler, Consumer<T> done) {
        Callback<T> thiz = this;

        Callback<Void> h = new Callback<Void>() {
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicInteger completed = new AtomicInteger(0);
            int depth;

            @Override
            public void handle(AsyncResult<Void> result) {
                depth++;
                //System.out.println("forEach call:" + cnt + " " + completed + " " + depth);
                if (result.failed()) {
                    //System.out.println("forEach stopped");
                    thiz.fail(result.cause());
                    return;
                }
                if (depth == 1) {
                    while (true) {
                        //System.out.println("forEach loop:" + cnt + " " + completed);
                        int i = cnt.get();
                        if (i >= list.size()) {
                            if (completed != null && i == completed.get()) {
                                completed = null;
                                done.accept(null);
                            }
                            //log.trace("forEach.done {} items", list.size());
                            break;
                        }

                        cnt.incrementAndGet();
                        //System.out.println("forEach accept");
                        elmHandler.accept(list.get(i), this);
                        if (completed == null || cnt.get() > completed.get()) {
                            break;
                        }

                    }
                }
                //System.out.println("forEach exit:" + cnt + " " + completed);
                depth--;
            }

            @Override
            public void ok(Void t) {
                completed.incrementAndGet();
                Callback.super.ok(t);
            }

            @Override
            public void ok() {
                completed.incrementAndGet();
                Callback.super.ok();
            }

        };
        h.handle(Future.succeededFuture());
    }
    
    default <E> void forEach(ReadStream<E> readStream, BiConsumer<E, Callback<Void>> elmHandler, Consumer<T> done) {
        readStream.exceptionHandler(error -> {
            fail(error);
        });
        readStream.endHandler($ -> {
            done.accept(null);
        });
        readStream.handler(elm -> {
            readStream.pause();
            elmHandler.accept(elm, result -> {
                if (result.failed()) {
                    fail(result.cause());
                    return;
                }
                readStream.resume();
            });
        });
    }
}
