package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;

import java.util.*;

public class MapaActivity extends ActionBarActivity
{
	private static BitmapDescriptor íconoAntenita;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);

		if(savedInstanceState == null)
		{
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MapaFragment())
					.commit();
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		((Aplicacion)getApplication()).reportActivityStart(this);
	}

	@Override
	protected void onStop()
	{
		((Aplicacion)getApplication()).reportActivityStop(this);
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.mapa, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		if(id == R.id.action_settings)
		{
			Intent i = new Intent(this, PreferenciasActivity.class);
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public static class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener
	{
		private GoogleMap mapa;

		private final Map<País, List<Marker>> países = new EnumMap<>(País.class);

		public MapaFragment()
		{
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			return inflater.inflate(R.layout.fragment_mapa, container, false);
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			FragmentActivity act = getActivity();
			mapa = ((SupportMapFragment)act.getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
			if(mapa == null)
			{
				Toast.makeText(act, act.getString(R.string.fallo_inicializar_mapa), Toast.LENGTH_SHORT).show();
				act.finish();
				return;
			}
			mapa.setMyLocationEnabled(true);
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
			if(AntenaActivity.coordsUsuario != null)
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
			if(íconoAntenita == null)
				íconoAntenita = BitmapDescriptorFactory.fromResource(R.drawable.antena);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
			prefs.registerOnSharedPreferenceChangeListener(this);
			ponerMarcadores();
			act.findViewById(R.id.map).post(new Runnable() {
				@Override
				public void run()
				{
					ActionBarActivity activity = (ActionBarActivity)getActivity();
					if(activity == null)
						return;
					ActionBar actionBar = activity.getSupportActionBar();
					if(actionBar == null)
						return;
					int height = actionBar.getHeight();
					mapa.setPadding(0, height, 0, 0);
				}
			});
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			if(key.startsWith("mapa_país_"))
				ponerMarcadores();
		}

		private void ponerMarcadores()
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			for(País país : País.values())
			{
				List<Marker> markers = países.get(país);
				if(prefs.getBoolean("mapa_país_" + país, true))
				{
					if(markers == null)
					{
						List<Antena> antenas = Antena.dameAntenas(getActivity(), país);
						markers = new ArrayList<>(antenas.size());
						países.put(país, markers);
						for(Antena antena : antenas)
						{
							markers.add(mapa.addMarker(new MarkerOptions().position(antena.getLatLng()).title(antena.toString()).icon(íconoAntenita)));
						}
					}
				} else
				{
					if(markers != null)
					{
						for(Marker marker : markers)
							marker.remove();
						países.remove(país);
					}
				}
			}
		}
	}
}
