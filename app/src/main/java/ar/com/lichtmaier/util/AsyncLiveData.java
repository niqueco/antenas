package ar.com.lichtmaier.util;

import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class AsyncLiveData<T> extends LiveData<T>
{
	private Future<T> future;
	private boolean loaded = false;

	private AsyncLiveData(boolean loadImmediately)
	{
		if(loadImmediately)
			load();
	}

	private AsyncLiveData()
	{
		this(true);
	}

	private void load()
	{
		future = ((ExecutorService)AsyncTask.THREAD_POOL_EXECUTOR).submit(() -> {
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
		return new AsyncLiveData<T>()
		{
			@Override
			protected T loadInBackground()
			{
				try
				{
					return callable.call();
				} catch(Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		};
	}
}
