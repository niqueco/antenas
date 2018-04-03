package ar.com.lichtmaier.util;

import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.util.concurrent.*;

public class AsyncLiveData<T> extends LiveData<T>
{
	@NonNull private final Callable<T> callable;
	@Nullable private FutureTask<T> future;
	private boolean loaded = false;
	@Nullable private final ErrorHandler onError;
	@Nullable private final Runnable doFinally;
	@NonNull private final Executor executor;

	private AsyncLiveData(@NonNull Callable<T> callable, @Nullable ErrorHandler onError, @Nullable Runnable doFinally, boolean loadImmediately, @NonNull Executor executor)
	{
		this.callable = callable;
		this.onError = onError;
		this.doFinally = doFinally;
		this.executor = executor;
		if(loadImmediately)
			load();
	}

	private void load()
	{
		future = new FutureTask<T>(callable) {
			@Override
			public void run()
			{
				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				super.run();
			}

			@Override
			protected void done()
			{
				try
				{
					if(!isCancelled())
						postValue(get());
				} catch(ExecutionException e)
				{
					if(onError == null)
						Log.e("antenas", "AsyncLiveData", e.getCause());
					else
						onError.onError(e.getCause());
				} catch(InterruptedException e)
				{
					Log.e("antenas", "AsyncLiveData", e);
				}
				synchronized(AsyncLiveData.this) {
					future = null;
					loaded = true;
				}
				if(doFinally != null)
					doFinally.run();
			}
		};
		executor.execute(future);
	}

	@VisibleForTesting
	public Future<T> getFuture()
	{
		return future;
	}

	@Override
	protected synchronized void onInactive()
	{
		if(future != null)
		{
			future.cancel(false);
			future = null;
		}
	}

	@Override
	protected synchronized void onActive()
	{
		if(future == null && !loaded)
			load();
	}

	public static <T> AsyncLiveData<T> create(@NonNull Callable<T> callable)
	{
		return create(callable, null, null, AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static <T> AsyncLiveData<T> create(@NonNull Callable<T> callable, @Nullable ErrorHandler onError, @Nullable Runnable doFinally, @NonNull Executor executor)
	{
		return new AsyncLiveData<>(callable, onError, doFinally, true, executor);
	}

	interface ErrorHandler
	{
		void onError(Throwable e);
	}
}
