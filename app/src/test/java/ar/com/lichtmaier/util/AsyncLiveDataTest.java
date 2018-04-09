package ar.com.lichtmaier.util;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AsyncLiveDataTest
{
	private ThreadPoolExecutor executor;

	@Before
	public void before()
	{
		executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(8);
	}

	@After
	public void after()
	{
		executor.shutdownNow();
	}

	@Test
	public void create() throws InterruptedException
	{
		System.out.println("hola");
		AtomicInteger x = new AtomicInteger(0);
		AtomicInteger y = new AtomicInteger(0);
		AsyncLiveData<String> ld = AsyncLiveData.create(() -> "!", e -> x.incrementAndGet(), y::incrementAndGet, executor);
		Observer<String> observer = a -> assertEquals("!", a);
		ld.observeForever(observer);
		Thread.sleep(50);
		ShadowLooper.runMainLooperToNextTask();
		assertEquals("!", ld.getValue());
		assertEquals(0, x.get());
		assertEquals(1, y.get());
		ld.removeObserver(observer);
	}

	@Test
	public void doFinallyConError() throws InterruptedException
	{
		System.out.println("hola");
		AtomicInteger x = new AtomicInteger(0);
		AtomicInteger y = new AtomicInteger(0);
		AsyncLiveData<String> ld = AsyncLiveData.create(() -> { throw new RuntimeException(); }, e -> x.incrementAndGet(), y::incrementAndGet, executor);
		Observer<String> observer = a -> assertEquals("!", a);
		ld.observeForever(observer);
		Thread.sleep(50);
		ShadowLooper.runMainLooperToNextTask();
		assertNull(ld.getValue());
		assertEquals(1, x.get());
		assertEquals(1, y.get());
		ld.removeObserver(observer);
	}


	@Test
	public void createMuchos() throws InterruptedException
	{
		List<AsyncLiveData<String>> l = new ArrayList<>();
		AtomicInteger x = new AtomicInteger(0);
		AtomicInteger y = new AtomicInteger(0);
		AtomicInteger z = new AtomicInteger(0);

		Observer<String> observer = a -> assertEquals("!", a);

		Random rnd = new Random();

		int N = 10000;

		for(int i = 0; i < N; i++)
		{
			AsyncLiveData<String> ld;

			ld = AsyncLiveData.create(() -> {
				Thread.sleep(rnd.nextInt(30));
				return "!";
				}, e -> assertTrue(false), x::incrementAndGet, executor);
			ld.observeForever(observer);
			l.add(ld);

			ld = AsyncLiveData.create(() -> {
				Thread.sleep(rnd.nextInt(30));
				throw new RuntimeException();
			}, e -> y.incrementAndGet(), z::incrementAndGet, executor);
			ld.observeForever(observer);
			l.add(ld);
		}
		while(!executor.getQueue().isEmpty())
			Thread.sleep(10);
		Thread.sleep(200);
		ShadowLooper.runMainLooperToNextTask();
		assertEquals(N, x.get());
		assertEquals(N, y.get());
		assertEquals(N, z.get());
		for(LiveData<String> ld : l)
			ld.removeObserver(observer);
	}

}