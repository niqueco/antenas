package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationRequest;

import org.gavaghan.geodesy.GlobalCoordinates;

public class TVActivity extends FragmentActivity implements LocationClientCompat.Callback
{
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;
	public static final int PRECISIÓN_ACEPTABLE = 150;

	private GlobalCoordinates coordsUsuario;
	private LocationClientCompat locationClient;
	private AntenasAdapter antenasAdapter;
	private LocationManager locationManager;
	private SharedPreferences prefs;

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
			nuevaUbicación(location);
		}
	};

	private final AntenasAdapter.Callback antenasAdapterListener = new AntenasAdapter.Callback()
	{
		@Override
		public void onAntenaClicked(Antena antena, View view)
		{

		}

		@Override
		public void onAdapterReady()
		{
			terminarDeConfigurar();
		}
	};

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.tv_activity);

		ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
		{
			prenderAnimación = new PrenderAnimación(pb);
			pb.postDelayed(prenderAnimación, 400);
		}

		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		final RecyclerView rv = (RecyclerView)findViewById(R.id.antenas);

		if(rv != null)
		{
			antenasAdapter = new AntenasAdapter(this, null, antenasAdapterListener, R.layout.antena_tv);
			rv.setAdapter(antenasAdapter);
		}

		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
		} else
		{
			crearLocationClientCompat();
		}
	}

	private void crearLocationClientCompat()
	{
		locationClient = new LocationClientCompat(this, LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(2000)
				.setSmallestDisplacement(10), this);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_FINE_LOCATION)
		{
			if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
				//noinspection ResourceType
				crearLocationClientCompat();
			else
				finish();
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if(locationManager != null)
		{
			pedirUbicaciónALocationManager();
		}
		if(locationClient != null)
			locationClient.onResume();
	}

	private void pedirUbicaciónALocationManager()
	{
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		criteria.setCostAllowed(true);
		try
		{
			//noinspection MissingPermission
			Compat.requestLocationUpdates(locationManager, 1000 * 60, 0, criteria, locationListener);
		} catch(IllegalArgumentException e)
		{
			Log.e("antenas", "Error pidiendo updates de GPS", e);
			Toast.makeText(this, getString(R.string.no_ubicacion), Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if(locationManager != null)
			//noinspection MissingPermission
			locationManager.removeUpdates(locationListener);
		if(locationClient != null)
			locationClient.onPause();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		if(locationClient != null)
			locationClient.onStart();
	}

	@Override
	protected void onStop()
	{
		if(locationClient != null)
			locationClient.onStop();
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		if(antenasAdapter != null)
			antenasAdapter.onDestroy();
		super.onDestroy();
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		//noinspection MissingPermission
		Location location = locationClient.getLastLocation();
		if(location != null)
			nuevaUbicación(location);
		//noinspection MissingPermission
		locationClient.onConnected();
	}

	private void nuevaUbicación(Location location)
	{
		Log.d("antenas", "loc="+location);
		coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
		antenasAdapter.nuevaUbicación(coordsUsuario);
	}

	/** Se llama cuando antenasAdapter avisa que ya está toda la información. */
	private void terminarDeConfigurar()
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		final ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
		{
			if(prenderAnimación.comienzoAnimación != -1)
			{
				long falta = 600 - (System.currentTimeMillis() - prenderAnimación.comienzoAnimación);
				if(falta <= 0)
					pb.setVisibility(View.GONE);
				else
					pb.postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							pb.setVisibility(View.GONE);
						}
					}, falta);
			} else
			{
				prenderAnimación.cancelado = true;
				pb.removeCallbacks(prenderAnimación);
			}
			prenderAnimación = null;
		}
		TextView problema = (TextView)findViewById(R.id.problema);
		if(antenasAdapter.getItemCount() == 0)
		{
			//StringBuilder sb = new StringBuilder(getString(R.string.no_se_encontraron_antenas, Formatos.formatDistance(this, maxDist)));
			//String[] vv = getResources().getStringArray(R.array.pref_max_dist_values);
			//if(Integer.parseInt(vv[vv.length-1]) * 1000 != maxDist)
			//	sb.append(' ').append(getString(R.string.podes_incrementar_radio));
			//String message = sb.toString();
			String message = getString(R.string.no_se_encontraron_antenas, Formatos.formatDistance(this, maxDist));
			problema.setText(message);
			problema.setVisibility(View.VISIBLE);
		} else
		{
			problema.setVisibility(View.GONE);
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult r)
	{
		if(r.hasResolution())
		{
			Log.w("antenas", "Play Services: " + r);
			try
			{
				r.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch(IntentSender.SendIntentException e)
			{
				Log.e("antenas", "uh?", e);
			}
		} else
		{
			Log.e("antenas", "Play Services no disponible: " + r + ". No importa, sobreviviremos.");

			locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			locationClient = null;
			if(coordsUsuario == null)
			{
				//noinspection MissingPermission
				Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if(location != null && location.getAccuracy() < PRECISIÓN_ACEPTABLE)
					nuevaUbicación(location);
			}
			pedirUbicaciónALocationManager();
		}
	}

	@Override
	public void onLocationChanged(Location location)
	{
		nuevaUbicación(location);
	}

	private PrenderAnimación prenderAnimación;

	private static class PrenderAnimación implements Runnable
	{
		private final View pb;
		public long comienzoAnimación = -1;
		boolean cancelado = false;

		public PrenderAnimación(View pb)
		{
			this.pb = pb;
		}

		@Override
		public void run()
		{
			if(cancelado)
				return;
			pb.setVisibility(View.VISIBLE);
			comienzoAnimación = System.currentTimeMillis();
		}
	}
}
