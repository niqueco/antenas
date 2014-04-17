package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;

public class Compat
{
	final private static CompatImpl impl;

	static {
		if(Build.VERSION.SDK_INT >= 9)
			impl = new CompatImplJB();
		else
			impl = new CompatImpl();
	}

	static class CompatImpl
	{
		void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance, Criteria criteria, LocationListener locationListener)
		{
			String provider = locationManager.getBestProvider(criteria, true);
			locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener);
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	static class CompatImplJB extends CompatImpl
	{
		@Override
		void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance,
				Criteria criteria, LocationListener locationListener)
		{
			locationManager.requestLocationUpdates(minTime, minDistance, criteria, locationListener, null);
		}
	}

	static void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance, Criteria criteria, LocationListener locationListener)
	{
		impl.requestLocationUpdates(locationManager, minTime, minDistance, criteria, locationListener);
	}
}
