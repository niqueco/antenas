package ar.com.lichtmaier.util;

import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AsyncLiveData<T> extends LiveData<T>
{
	private Future<T> future;
	private boolean loaded = false;
	private final ExecutorService executor;

	private AsyncLiveData(boolean loadImmediately, ExecutorService executor)
	{
		this.executor = executor;
		if(loadImmediately)
			load();
	}

	private void load()
	{
		future = executor.submit(() -> {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			T r = null;
			try
			{
				r = loadInBackground();
				postValue(r);
			} catch(Exception e)
			{
				Log.e("antenas", "AsyncLiveData", e);
			}
			synchronized(AsyncLiveData.this) {
				future = null;
				loaded = true;
			}
			return r;
		});
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

	@WorkerThread
	protected abstract T loadInBackground();

	public static <T> AsyncLiveData<T> create(Callable<T> callable)
	{
		return create(callable, null, null);
	}

	public static <T> AsyncLiveData<T> create(Callable<T> callable, @Nullable ErrorHandler onError, @Nullable Runnable doFinally)
	{
		Executors.newCachedThreadPool();
		return create(callable, onError, doFinally, (ExecutorService)AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static <T> AsyncLiveData<T> create(Callable<T> callable, @Nullable ErrorHandler onError, @Nullable Runnable doFinally, ExecutorService executor)
	{
		return new AsyncLiveData<T>(true, executor)
		{
			@Override
			protected T loadInBackground()
			{
				try
				{
					return callable.call();
				} catch(Exception e)
				{
					if(onError != null)
						onError.onError(e);
					throw new RuntimeException(e);
				} finally
				{
					if(doFinally != null)
						doFinally.run();
				}
			}
		};
	}

	interface ErrorHandler
	{
		void onError(Exception e);
	}
}
