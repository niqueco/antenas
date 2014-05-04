package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.view.View;

public class Compat
{
	final private static CompatImpl impl;

	static {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			impl = new CompatImplHC();
		else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
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

		void disableHardwareAccelerationForLineCaps(FlechaView view) { }
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

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static class CompatImplHC extends CompatImplJB
	{
		@Override
		public void disableHardwareAccelerationForLineCaps(FlechaView view)
		{
			if(view.isHardwareAccelerated() && Build.VERSION.SDK_INT < 18)
				view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
	}

	static void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance, Criteria criteria, LocationListener locationListener)
	{
		impl.requestLocationUpdates(locationManager, minTime, minDistance, criteria, locationListener);
	}

	/** Disable hardware acceleration if line caps won't work.
	 *
	 * @param view the target view.
	 */
	public static void disableHardwareAccelerationForLineCaps(FlechaView view)
	{
		impl.disableHardwareAccelerationForLineCaps(view);
	}
}
