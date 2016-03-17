package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.app.Application;
import android.os.StrictMode;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

public class Aplicacion extends Application
{
	private Tracker tracker;

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
	}

	private Tracker getTracker()
	{
		if(tracker == null)
		{
			GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
			if(Publicidad.crearAdRequestBuilder().build().isTestDevice(this))
				analytics.setDryRun(true);
			analytics.enableAutoActivityReports(this);
			tracker = analytics.newTracker(R.xml.analytics);
			tracker.enableAdvertisingIdCollection(true);
		}
		return tracker;
	}

	void reportActivityStart(Activity act)
	{
		getTracker();
		GoogleAnalytics.getInstance(this).reportActivityStart(act);
	}

	void reportActivityStop(Activity act)
	{
		GoogleAnalytics.getInstance(this).reportActivityStop(act);
	}

	public void mandarEvento(String categoría, String acción)
	{
		HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder()
				.setCategory(categoría)
				.setAction(acción);
		getTracker().send(builder.build());
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
