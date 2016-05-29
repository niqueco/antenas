package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.View;

public class Compat
{
	final private static CompatImpl impl;

	static {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			impl = new CompatImplJB1();
		else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			impl = new CompatImplHC();
		else
			impl = new CompatImpl();
	}

	static class CompatImpl
	{

		void disableHardwareAccelerationForLineCaps(FlechaView view) { }

		boolean activityIsDestroyed(Activity activity)
		{
			return false;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static class CompatImplHC extends CompatImpl
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

	/** Disable hardware acceleration if line caps won't work.
	 *
	 * @param view the target view.
	 */
	public static void disableHardwareAccelerationForLineCaps(FlechaView view)
	{
		impl.disableHardwareAccelerationForLineCaps(view);
	}

	public static boolean activityIsDestroyed(Activity activity)
	{
		return impl.activityIsDestroyed(activity);
	}
}
