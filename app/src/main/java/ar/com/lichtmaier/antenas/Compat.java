package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.RequiresPermission;
import android.view.View;

public class Compat
{
	final private static CompatImpl impl;

	static {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			impl = new CompatImplJB1();
		else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			impl = new CompatImplHC();
		else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
			impl = new CompatImplGB();
		else
			impl = new CompatImpl();
	}

	static class CompatImpl
	{
		@RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
		void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance, Criteria criteria, LocationListener locationListener)
		{
			String provider = locationManager.getBestProvider(criteria, true);
			locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener);
		}

		void disableHardwareAccelerationForLineCaps(FlechaView view) { }

		public void applyPreferences(SharedPreferences.Editor editor)
		{
			editor.commit();
		}

		boolean activityIsDestroyed(Activity activity)
		{
			return false;
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	static class CompatImplGB extends CompatImpl
	{
		@Override
		@RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
		void requestLocationUpdates(LocationManager locationManager, int minTime, int minDistance,
				Criteria criteria, LocationListener locationListener)
		{
			locationManager.requestLocationUpdates(minTime, minDistance, criteria, locationListener, null);
		}

		@Override
		public void applyPreferences(SharedPreferences.Editor editor)
		{
			editor.apply();
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static class CompatImplHC extends CompatImplGB
	{
		@Override
		public void disableHardwareAccelerationForLineCaps(FlechaView view)
		{
			if(view.isHardwareAccelerated() && Build.VERSION.SDK_INT < 18)
				view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	static class CompatImplJB1 extends CompatImplHC
	{
		boolean activityIsDestroyed(Activity activity)
		{
			return activity.isDestroyed();
		}
	}

	@RequiresPermission(anyOf = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
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

	public static void applyPreferences(SharedPreferences.Editor editor)
	{
		impl.applyPreferences(editor);
	}

	public static boolean activityIsDestroyed(Activity activity)
	{
		return impl.activityIsDestroyed(activity);
	}
}
