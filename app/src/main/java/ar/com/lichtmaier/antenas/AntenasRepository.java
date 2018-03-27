package ar.com.lichtmaier.antenas;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AntenasRepository
{
	private final Context context;

	private final static String TAG = "antrepo";

	static class AntenaListada
	{
		final Antena antena;
		final double distancia;
		final boolean lejos;

		AntenaListada(Antena antena, double distancia, boolean lejos)
		{
			this.antena = antena;
			this.distancia = distancia;
			this.lejos = lejos;
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append('{').append(antena).append(' ').append((int)distancia).append('m');
			if(lejos)
				sb.append(" lejos");
			sb.append('}');
			return sb.toString();
		}
	}

	AntenasRepository(Context context)
	{
		this.context = context.getApplicationContext();
	}

	LiveData<List<AntenaListada>> dameAntenasAlrededor(LiveData<Location> locationLiveData)
	{
		return new AntenasAlrededorLiveData(locationLiveData);
	}

	private class AntenasAlrededorLiveData extends MediatorLiveData<List<AntenaListada>> implements SharedPreferences.OnSharedPreferenceChangeListener
	{
		private final SharedPreferences prefs;
		private LiveData<List<Antena>> ldac;
		private final LiveData<Location> locationLiveData;

		final private Map<Antena, Boolean> cachéCercaníaAntena = new HashMap<>();
		private LatLng posCachéCercanía;

		// últimos valores de preferencias usados
		private int maxDist;
		private boolean mostrarMenos, usarContornos;

		AntenasAlrededorLiveData(LiveData<Location> locationLiveData)
		{
			this.locationLiveData = locationLiveData;
			prefs = PreferenceManager.getDefaultSharedPreferences(context);
			addSource(locationLiveData, (loc) -> {
				if(Log.isLoggable(TAG, Log.DEBUG))
					Log.d(TAG, "loc: " + loc);
				if(loc == null)
					return;
				process(loc);
			});
		}

		@Override
		protected void onActive()
		{
			super.onActive();
			prefs.registerOnSharedPreferenceChangeListener(this);

			// si sólo cambió usarContornos habría que solamente forzar el procesado de una nueva ubicación, pero... fiaca
			if(prefs.getInt("max_dist", 60000) != maxDist || prefs.getBoolean("menos", true) != mostrarMenos || prefs.getBoolean("usar_contornos", true) != usarContornos)
				process();
		}

		@Override
		protected void onInactive()
		{
			super.onInactive();
			prefs.unregisterOnSharedPreferenceChangeListener(this);
		}

		private void process()
		{
			process(locationLiveData.getValue());
		}

		private void process(Location location)
		{
			if(location == null)
				return;
			maxDist = prefs.getInt("max_dist", 60000);
			if(Log.isLoggable(TAG, Log.DEBUG))
				Log.d(TAG, "AntenasAlrededorLiveData: procesando loc " + location + " con max_dist " + maxDist);
			if(ldac != null)
				removeSource(ldac);
			GlobalCoordinates gcoords = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
			mostrarMenos = prefs.getBoolean("menos", true);
			ldac = Antena.dameAntenasCerca(context, gcoords, maxDist, mostrarMenos);

			addSource(ldac, antenasAlrededor -> {

				if(Log.isLoggable(TAG, Log.DEBUG))
					Log.d(TAG, "alrededor: " + antenasAlrededor);

				CachéDeContornos cdc = CachéDeContornos.dameInstancia(context);
				try
				{
					if(antenasAlrededor == null)
						return;

					LatLng coords = new LatLng(location.getLatitude(), location.getLongitude());

					boolean renovarCaché = false;
					if(posCachéCercanía != null)
					{
						if(SphericalUtil.computeDistanceBetween(posCachéCercanía, coords) > 200)
						{
							if(Log.isLoggable(TAG, Log.DEBUG))
								Log.d(TAG, "Renuevo caché de cercanía de " + cachéCercaníaAntena.size() + " elementos.");
							renovarCaché = true;
							posCachéCercanía = coords;
							cachéCercaníaAntena.keySet().retainAll(antenasAlrededor);
						}
					} else
					{
						posCachéCercanía = coords;
					}

					usarContornos = prefs.getBoolean("usar_contornos", true);
					boolean noUsarContornos = !usarContornos;
					List<AntenaListada> res = new ArrayList<>();
					List<AntenaListada> antenasLejos = new ArrayList<>();
					for(Antena a : antenasAlrededor)
					{
						boolean estamosUsandoContornos = a.país == País.US && !noUsarContornos;
						Boolean cerca = estamosUsandoContornos ? cachéCercaníaAntena.get(a) : Boolean.TRUE;
						boolean lejos = cerca != null && !cerca;
						AntenaListada al = new AntenaListada(a, a.distanceTo(gcoords), lejos);
						if(lejos)
							antenasLejos.add(al);
						else
							res.add(al);
						if(estamosUsandoContornos && (renovarCaché || cerca == null))
						{
							LiveData<Boolean> ec = cdc.enContorno(a, coords);
							addSource(ec, enContorno -> {
								removeSource(ec);
								cachéCercaníaAntena.put(a, enContorno);
								processDemorado();
							});
						}
					}
					res.addAll(antenasLejos);
					setValue(res);
				} finally
				{
					cdc.devolver();
				}
			});
		}

		final private Handler handler = new LlamarAProcesar(this);

		void processDemorado()
		{
			if(!handler.hasMessages(1))
				handler.sendEmptyMessageDelayed(1, 1000);
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			if(key.equals("max_dist") || key.equals("menos") || key.equals("usar_contornos"))
				process();
		}
	}

	private static class LlamarAProcesar extends Handler
	{
		private final WeakReference<AntenasAlrededorLiveData> ref;

		private LlamarAProcesar(AntenasAlrededorLiveData ld)
		{
			ref = new WeakReference<>(ld);
		}

		@Override
		public void handleMessage(Message msg)
		{
			AntenasAlrededorLiveData ld = ref.get();
			if(ld != null)
				ld.process();
		}
	}
}
