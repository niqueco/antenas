package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.app.Application;

@SuppressWarnings("UnusedParameters")
public class Aplicacion extends Application
{
	void reportActivityStart(Activity act) { }

	void reportActivityStop(Activity act) { }

	void mandarEvento(String categoría, String acción) { }

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
