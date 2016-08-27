package ar.com.lichtmaier.antenas;

import android.app.Application;
import android.os.StrictMode;

import com.google.firebase.analytics.FirebaseAnalytics;

public class Aplicacion extends Application
{
	@Override
	public void onCreate()
	{
		super.onCreate();

		if(BuildConfig.DEBUG)
		{
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				.detectAll()
				.penaltyLog()
				.build());
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				.detectAll()
				.penaltyLog()
				.build());
		}

		if(Publicidad.crearAdRequestBuilder().build().isTestDevice(this))
			FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false);
	}

	@Override
	public void onTrimMemory(int level)
	{
		super.onTrimMemory(level);
		if(level >= TRIM_MEMORY_MODERATE)
			CachéDeContornos.vaciarCache();
	}

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		CachéDeContornos.vaciarCache();
	}
}
