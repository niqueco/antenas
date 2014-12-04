package ar.com.lichtmaier.antenas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.*;

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

	public static class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener, GoogleMap.OnInfoWindowClickListener, GoogleMap.InfoWindowAdapter
	{
		private GoogleMap mapa;

		private final Map<País, List<Marker>> países = new EnumMap<>(País.class);

		private final Map<Marker, Antena> markerAAntena = new HashMap<>();

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
			SupportMapFragment mapFragment = (SupportMapFragment)getChildFragmentManager().findFragmentById(R.id.map);
			mapa = mapFragment.getMap();
			if(mapa == null)
			{
				Toast.makeText(act, act.getString(R.string.fallo_inicializar_mapa), Toast.LENGTH_SHORT).show();
				act.finish();
				return;
			}
			mapa.setMyLocationEnabled(true);
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
			mapa.setOnInfoWindowClickListener(this);
			mapa.setInfoWindowAdapter(this);
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
			Activity act = getActivity();
			if(act == null)
				return;
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
			for(País país : País.values())
			{
				List<Marker> markers = países.get(país);
				if(prefs.getBoolean("mapa_país_" + país, true))
				{
					if(markers == null)
					{
						List<Antena> antenas = Antena.dameAntenas(act, país);
						markers = new ArrayList<>(antenas.size());
						países.put(país, markers);
						for(Antena antena : antenas)
						{
							Marker marker = mapa.addMarker(new MarkerOptions()
									.position(antena.getLatLng())
									.title(antena.toString())
									.icon(íconoAntenita));
							markerAAntena.put(marker, antena);
							markers.add(marker);
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

		@Override
		public void onInfoWindowClick(Marker marker)
		{
			Antena antena = markerAAntena.get(marker);
			antena.mostrarInformacion(getActivity());
		}

		@Override
		public View getInfoWindow(Marker marker)
		{
			return null;
		}

		@Override
		public View getInfoContents(Marker marker)
		{
			Antena antena = markerAAntena.get(marker);
			if(antena.canales == null)
				return null;
			boolean hayImágenes = antena.hayImágenes();
			ContextThemeWrapper ctx = new ContextThemeWrapper(getActivity(), R.style.InfoMapa);
			@SuppressLint("InflateParams")
			View v = ((LayoutInflater)ctx.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.info_mapa, null, false);
			ViewGroup l = (ViewGroup)v.findViewById(R.id.lista_canales);
			int n;
			if(l instanceof TableLayout)
			{
				n = Math.min(antena.canales.size(), 8);
				for(int i = 0; i < (n+1) / 2 ; i++)
				{
					TableRow row = new TableRow(ctx);
					row.addView(antena.canales.get(i * 2).dameViewCanal(ctx, row, hayImágenes));
					if((i*2+1) < antena.canales.size())
					{
						View der = antena.canales.get(i * 2 + 1).dameViewCanal(ctx, row, hayImágenes);
						der.setPadding((int)getResources().getDimension(der.getPaddingLeft() + R.dimen.paddingColumnasInfoMapa), der.getPaddingTop(), der.getPaddingRight(), der.getPaddingBottom());
						row.addView(der);
					}
					l.addView(row);
				}
			} else
			{
				n = Math.min(antena.canales.size(), 4);
				for(Canal canal : antena.canales)
					l.addView(canal.dameViewCanal(ctx, l, hayImágenes));
			}
			if(n < antena.canales.size())
			{
				TextView tv = new TextView(ctx);
				tv.setText(ctx.getString(R.string.some_more, antena.canales.size() - n));
				tv.setLayoutParams(
						(l instanceof TableLayout)
						? new TableLayout.LayoutParams()
						: new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
				);
				tv.setGravity(Gravity.CENTER);
				l.addView(tv);
			}
			v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			return v;
		}
	}
}
