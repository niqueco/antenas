package ar.com.lichtmaier.antenas;

import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;

import org.gavaghan.geodesy.GlobalCoordinates;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

public class AntenaActivity extends ActionBarActivity implements SensorEventListener, GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener
{
	final private Map<Antena, View> antenaAVista = new HashMap<>();
	final private Map<View, Antena> vistaAAntena = new HashMap<>();
	static GlobalCoordinates coordsUsuario;
	final private float[] gravity = new float[3];
	final private float[] geomagnetic = new float[3];
	private SensorManager sensorManager;
	private Sensor acelerómetro;
	private Sensor magnetómetro;
	private boolean hayInfoDeMagnetómetro = false, hayInfoDeAcelerómetro = false;

	private LocationManager locationManager;
	private LocationRequest locationRequest;

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		@Override
		public void onProviderEnabled(String provider) { }
		@Override
		public void onProviderDisabled(String provider) { }

		@Override
		public void onLocationChanged(Location location)
		{
			if(location.getAccuracy() > 300)
				return;
			coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
			nuevaUbicación();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_antena);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
			{
				nuevaUbicación();
			}
		});

		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magnetómetro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		acelerómetro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		if(Build.VERSION.SDK_INT < 8 || GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS)
		{
			locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		} else
		{
			locationClient = new LocationClient(this, this, this);
			locationRequest = LocationRequest.create();
			locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
			locationRequest.setInterval(10000);
			locationRequest.setFastestInterval(2000);
		}

		if(savedInstanceState != null && savedInstanceState.containsKey("lat"))
		{
			coordsUsuario = new GlobalCoordinates(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon"));
			nuevaUbicación();
		} else if(locationClient == null)
		{
			Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if(location != null && location.getAccuracy() < 300)
			{
				coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
				nuevaUbicación();
			} else
			{
				locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if(location != null && location.getAccuracy() < 300)
				{
					coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
					nuevaUbicación();
				}
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if(coordsUsuario != null)
		{
			outState.putDouble("lat", coordsUsuario.getLatitude());
			outState.putDouble("lon", coordsUsuario.getLongitude());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.antena, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		Intent i;
		switch(id)
		{
			case R.id.action_settings:
				i = new Intent(this, PreferenciasActivity.class);
				startActivity(i);
				return true;
			case R.id.action_mapa:
				i = new Intent(this, MapaActivity.class);
				startActivity(i);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		if(locationClient != null)
			locationClient.connect();
	}

	protected void onResume()
	{
		super.onResume();
		sensorManager.registerListener(this, magnetómetro, SensorManager.SENSOR_DELAY_UI);
		sensorManager.registerListener(this, acelerómetro, SensorManager.SENSOR_DELAY_UI);
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		criteria.setCostAllowed(true);
		if(locationManager != null)
			Compat.requestLocationUpdates(locationManager, 1000 * 60, 0, criteria, locationListener);
	}

	@Override
	protected void onPause()
	{
		hayInfoDeAcelerómetro = false;
		hayInfoDeMagnetómetro = false;
		sensorManager.unregisterListener(this);
		if(locationManager != null)
			locationManager.removeUpdates(locationListener);
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		if(locationClient != null)
			locationClient.disconnect();
		super.onStop();
	}
	
	final private float[] r = new float[9];
	final private float[] values = new float[3];
	final private float[] r2 = new float[9];
	private LocationClient locationClient;
	private SharedPreferences prefs;
	void nuevaOrientación()
	{
		SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
		@SuppressWarnings("deprecation")
		int rotation = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
		int axisX;
		int axisY;
		switch(rotation)
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
				throw new RuntimeException("rot: " + rotation);
		}
		SensorManager.remapCoordinateSystem(r, axisX, axisY, r2);
		SensorManager.getOrientation(r2, values);
		double brújula = Math.toDegrees(values[0]);
		if(brújula < 0)
			brújula += 360;
		//NumberFormat nf = NumberFormat.getInstance(new Locale("es", "AR"));
		//((TextView)findViewById(R.id.orientacion)).setText(nf.format(brújula) /*+ " " + nf.format(Math.PI/2.0 - brújula)*/);
		//Log.d("antenas", "orientacion: " + values[0]);
		for(Entry<Antena, View> e : antenaAVista.entrySet())
		{
			Antena antena = e.getKey();
			double rumbo = antena.rumboDesde(coordsUsuario);
			FlechaView f = (FlechaView)e.getValue().findViewById(R.id.flecha);
			f.setÁngulo(rumbo - brújula);
		}
	}

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
			nuevaOrientación();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	private void nuevaUbicación()
	{
		List<Antena> antenasCerca = Antena.dameAntenasCerca(this, coordsUsuario,
				Integer.parseInt(prefs.getString("max_dist", "60")) * 1000,
				prefs.getBoolean("menos", true));
		Iterator<Entry<Antena, View>> it = antenaAVista.entrySet().iterator();
		ViewGroup contenedor = (ViewGroup)findViewById(R.id.antenas);
		while(it.hasNext())
		{
			Entry<Antena, View> e = it.next();
			if(!antenasCerca.contains(e.getKey()))
			{
				contenedor.removeView(e.getValue());
				vistaAAntena.remove(e.getValue());
				it.remove();
			}
		}
		NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "AR"));
		nf.setMaximumFractionDigits(1);
		LayoutInflater inf = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		for(Antena a : antenasCerca)
		{
			if(!antenaAVista.containsKey(a))
			{
				View v = inf.inflate(R.layout.antena, contenedor, false);
				int n = contenedor.getChildCount(), i;
				for(i = 0 ; i < n ; i++)
				{
					View child = contenedor.getChildAt(i);
					if(vistaAAntena.get(child).dist > a.dist)
					{
						contenedor.addView(v, i);
						break;
					}
				}
				if(i == n)
					contenedor.addView(v);
				((TextView)v.findViewById(R.id.antena_desc)).setText(a.toString());
				((TextView)v.findViewById(R.id.antena_dist)).setText(nf.format(a.distanceTo(coordsUsuario) / 1000.0) + " km");
				antenaAVista.put(a, v);
				vistaAAntena.put(v, a);
			}
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0)
	{
	}

	@Override
	public void onConnected(Bundle arg0)
	{
		Location location = locationClient.getLastLocation();
		if(location != null)
		{
			coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
			nuevaUbicación();
		}
		locationClient.requestLocationUpdates(locationRequest, this);
	}

	@Override
	public void onDisconnected()
	{
	}

	@Override
	public void onLocationChanged(Location location)
	{
		coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
		nuevaUbicación();
	}
}
