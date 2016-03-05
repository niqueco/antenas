package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

public class Brújula implements SensorEventListener
{
	final private float[] gravity = new float[3];
	final private float[] geomagnetic = new float[3];
	final private Sensor acelerómetro;
	final private Sensor magnetómetro;
	private boolean hayInfoDeMagnetómetro = false, hayInfoDeAcelerómetro = false;
	private final Callback callback;
	private float declinaciónMagnética = Float.MAX_VALUE;
	final private int rotación;

	private Brújula(Context context, Sensor acelerómetro, Sensor magnetómetro, Callback callback)
	{
		this.acelerómetro = acelerómetro;
		this.magnetómetro = magnetómetro;
		this.callback = callback;
		rotación = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
	}

	public static Brújula crear(Context context, Callback callback)
	{
		if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS))
		{
			SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
			Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if(mag != null && acc != null)
				return new Brújula(context, mag, acc, callback);
		}
		return null;
	}

	public void onResume(Context context)
	{
		SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		sm.registerListener(this, magnetómetro, SensorManager.SENSOR_DELAY_UI);
		sm.registerListener(this, acelerómetro, SensorManager.SENSOR_DELAY_UI);
	}

	public void onPause(Context context)
	{
		hayInfoDeAcelerómetro = false;
		hayInfoDeMagnetómetro = false;
		SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		sm.unregisterListener(this);
	}

	public void setCoordinates(double latitude, double longitude, float alturaUsuario)
	{
		if(declinaciónMagnética == Float.MAX_VALUE)
		{
			declinaciónMagnética = new GeomagneticField((float)latitude, (float)longitude, alturaUsuario, System.currentTimeMillis()).getDeclination();
			if(Log.isLoggable("antenas", Log.DEBUG))
				Log.d("antenas", "Declinación magnética: " + declinaciónMagnética);
		}
	}

	final private float[] r = new float[9];
	final private float[] values = new float[3];
	final private float[] r2 = new float[9];

	@SuppressWarnings("SuspiciousNameCombination")
	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if(event.sensor == magnetómetro)
		{
			System.arraycopy(event.values, 0, geomagnetic, 0, 3);
			hayInfoDeMagnetómetro = true;
		} else if(event.sensor == acelerómetro)
		{
			System.arraycopy(event.values, 0, gravity, 0, 3);
			hayInfoDeAcelerómetro = true;
		}
		if(hayInfoDeAcelerómetro && hayInfoDeMagnetómetro)
		{
			SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
			int axisX;
			int axisY;
			switch(rotación)
			{
				case Surface.ROTATION_0:
					axisX = SensorManager.AXIS_X;
					axisY = SensorManager.AXIS_Y;
					break;
				case Surface.ROTATION_90:
					axisX = SensorManager.AXIS_Y;
					axisY = SensorManager.AXIS_MINUS_X;
					break;
				case Surface.ROTATION_180:
					axisX = SensorManager.AXIS_MINUS_X;
					axisY = SensorManager.AXIS_MINUS_Y;
					break;
				case Surface.ROTATION_270:
					axisX = SensorManager.AXIS_MINUS_Y;
					axisY = SensorManager.AXIS_X;
					break;
				default:
					throw new RuntimeException("rot: " + rotación);
			}
			SensorManager.remapCoordinateSystem(r, axisX, axisY, r2);
			SensorManager.getOrientation(r2, values);
			double brújula = Math.toDegrees(values[0]);
			if(declinaciónMagnética != Float.MAX_VALUE)
				brújula += declinaciónMagnética;
			if(brújula < 0)
				brújula += 360;
			else if(brújula >= 360)
				brújula -= 360;
			callback.nuevaOrientación(brújula);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	interface Callback
	{
		void nuevaOrientación(double orientación);
	}
}
