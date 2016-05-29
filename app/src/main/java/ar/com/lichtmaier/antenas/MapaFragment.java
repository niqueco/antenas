package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.util.*;

public class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener,
		GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMapClickListener,
		GoogleMap.OnCameraChangeListener, GoogleMap.OnMarkerClickListener
{
	private GoogleMap mapa;

	private final Map<País, List<Marker>> países = new EnumMap<>(País.class);

	private final Map<Marker, Antena> markerAAntena = new HashMap<>();
	private Marker markerSeleccionado;
	private CachéDeContornos cachéDeContornos;
	private Polygon contornoActual;
	private int altoActionBar;
	final private EnumSet<País> paísesPrendidos = EnumSet.noneOf(País.class);
	private AsyncTask<Void, Void, Polígono> tareaTraerContorno;
	private int originalBackStackEntryCount;
	private Publicidad publicidad;

	private static BitmapDescriptor íconoAntenita, íconoAntenitaElegida;
	private static final int PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION = 145;
	private Canal canalSeleccionado;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if(savedInstanceState != null)
			originalBackStackEntryCount = savedInstanceState.getInt("originalBackStackEntryCount");
		else
			originalBackStackEntryCount = getFragmentManager().getBackStackEntryCount();

		getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener()
		{
			@Override
			public void onBackStackChanged()
			{
				if(getFragmentManager().getBackStackEntryCount() == originalBackStackEntryCount)
				{
					canalSeleccionado(null, null);
					if(markerSeleccionado != null)
					{
						markerSeleccionado.hideInfoWindow();
						markerSeleccionado.setIcon(íconoAntenita);
						markerSeleccionado = null;
					}
					mapa.setPadding(0, altoActionBar, 0, 0);
				}
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt("originalBackStackEntryCount", originalBackStackEntryCount);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		publicidad.onResume();
	}

	@Override
	public void onPause()
	{
		publicidad.onPause();
		super.onPause();
	}

	@Override
	public void onDetach()
	{
		publicidad.onDestroy();
		super.onDetach();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_mapa, container, false);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		publicidad = new Publicidad(getActivity(), "ca-app-pub-0461170458442008/5727485755");
		SupportMapFragment mapFragment = (SupportMapFragment)getChildFragmentManager().findFragmentById(R.id.map);
		mapFragment.getMapAsync(new OnMapReadyCallback()
		{
			@Override
			public void onMapReady(GoogleMap googleMap)
			{
				mapa = googleMap;
				inicializarMapa(savedInstanceState);
			}
		});
	}

	private void inicializarMapa(Bundle savedInstanceState)
	{
		if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION);
			return;
		}
		FragmentActivity act = getActivity();
		mapa.setMyLocationEnabled(true);
		if(savedInstanceState == null)
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
		mapa.setOnInfoWindowClickListener(this);
		mapa.setOnMapClickListener(this);
		mapa.setOnCameraChangeListener(this);
		mapa.setOnMarkerClickListener(this);
		Location loc = null;
		if(AntenaActivity.coordsUsuario != null)
		{
			if(savedInstanceState == null)
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
			loc = new Location("*");
			loc.setLatitude(AntenaActivity.coordsUsuario.getLatitude());
			loc.setLongitude(AntenaActivity.coordsUsuario.getLongitude());
		}
		publicidad.load(loc);
		if(íconoAntenita == null)
			íconoAntenita = BitmapDescriptorFactory.fromResource(R.drawable.antena);
		if(íconoAntenitaElegida == null)
			íconoAntenitaElegida = BitmapDescriptorFactory.fromResource(R.drawable.antena_seleccionada);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
		prefs.registerOnSharedPreferenceChangeListener(this);
		for(País país : País.values())
			if(prefs.getBoolean("mapa_país_" + país, false))
			{
				Antena.dameAntenas(act, país);
				paísesPrendidos.add(país);
			}
		AppCompatActivity activity = (AppCompatActivity)getActivity();
		ActionBar actionBar = activity.getSupportActionBar();
		if(actionBar != null)
			altoActionBar = actionBar.getHeight();

		if(altoActionBar == 0)
		{
			View v = getView();
			if(v == null)
				throw new RuntimeException("uh?");
			v.post(new Runnable()
			{
				@Override
				public void run()
				{
					AppCompatActivity activity = (AppCompatActivity)getActivity();
					if(activity == null)
						return;
					ActionBar actionBar = activity.getSupportActionBar();
					if(actionBar != null)
						altoActionBar = actionBar.getHeight();
					configurarPaddingMapa();
				}
			});
		} else
		{
			configurarPaddingMapa();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION)
		{
			if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
				inicializarMapa(null);
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if(key.startsWith("mapa_país_"))
		{
			País país = País.valueOf(key.substring(10));
			if(sharedPreferences.getBoolean(key, false))
			{
				Antena.dameAntenas(getActivity(), país);
				ponerMarcadores();
				paísesPrendidos.add(país);
			} else
			{
				List<Marker> markers = países.remove(país);
				if(markers != null)
					for(Marker marker : markers)
					{
						antenasDentro.remove(markerAAntena.get(marker));
						marker.remove();
					}
				paísesPrendidos.remove(país);
			}
		}
	}

	private void configurarPaddingMapa()
	{
		Fragment frCanales = getFragmentManager().findFragmentByTag("canales");
		configurarPaddingMapa(frCanales == null ? null : (ViewGroup)frCanales.getView());
	}

	void configurarPaddingMapa(ViewGroup v)
	{
		int height = 0;
		if(v != null)
		{
			boolean esTablet = v.getChildAt(0).getClass() == ScrollView.class;
			if(!esTablet)
				height = v.getHeight();
		}
		height -= publicidad.getHeight();
		if(height < 0)
			height = 0;
		mapa.setPadding(0, altoActionBar, 0, height);
	}

	public boolean mapaInicializado()
	{
		return mapa != null;
	}

	static class FuturoMarcador
	{
		final Antena antena;
		final MarkerOptions markerOptions;

		public FuturoMarcador(Antena antena, MarkerOptions markerOptions)
		{
			this.antena = antena;
			this.markerOptions = markerOptions;
		}
	}

	private void ponerMarcadores()
	{
		Activity act = getActivity();
		if(act == null)
			return;
		final LatLngBounds latLngBounds = mapa.getProjection().getVisibleRegion().latLngBounds;

		new AsyncTask<Void, Void, List<FuturoMarcador>>()
		{

			@Override
			protected List<FuturoMarcador> doInBackground(Void... params)
			{
				List<Antena> antenas = new ArrayList<>();
				Antena.antenasEnRectángulo(latLngBounds.northeast.latitude,
						latLngBounds.southwest.longitude,
						latLngBounds.southwest.latitude,
						latLngBounds.northeast.longitude,
						antenas);
				List<FuturoMarcador> mm = new ArrayList<>();
				for(Antena antena : antenas)
				{
					if(!paísesPrendidos.contains(antena.país))
						continue;
					if(!antenasDentro.add(antena))
						continue;

					mm.add(new FuturoMarcador(antena, new MarkerOptions()
							.position(antena.getLatLng())
							.title((antena.canales == null || antena.canales.isEmpty()) ? antena.dameNombre(getActivity()) : null)
							.icon(íconoAntenita)));
				}
				return mm;
			}

			@Override
			protected void onPostExecute(List<FuturoMarcador> mm)
			{
				for(FuturoMarcador m : mm)
				{
					Marker marker = mapa.addMarker(m.markerOptions);
					markerAAntena.put(marker, m.antena);

					List<Marker> markers = países.get(m.antena.país);
					if(markers == null)
					{
						markers = new ArrayList<>();
						países.put(m.antena.país, markers);
					}
					markers.add(marker);
				}
			}

		}.execute();
	}

	final private Set<Antena> antenasDentro = new HashSet<>();

	@Override
	public void onCameraChange(CameraPosition cameraPosition)
	{
		ponerMarcadores();
	}

	@Override
	public void onInfoWindowClick(Marker marker)
	{
		Antena antena = markerAAntena.get(marker);
		antena.mostrarInformacion(getActivity());
	}

	@Override
	public void onMapClick(LatLng latLng)
	{
		// Esto se hace igual al hacer pop del fragmento de la info,
		// pero por si no hay info también se hace acá.
		if(markerSeleccionado != null)
		{
			markerSeleccionado.hideInfoWindow();
			markerSeleccionado.setIcon(íconoAntenita);
			markerSeleccionado = null;
		}
		canalSeleccionado(null, null);
		FragmentManager fm = getFragmentManager();
		if(fm.findFragmentByTag("canales") != null)
			fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
		mapa.setPadding(0, altoActionBar, 0, 0);
	}

	@Override
	public boolean onMarkerClick(Marker marker)
	{
		if(markerSeleccionado != null)
			markerSeleccionado.setIcon(íconoAntenita);

		markerSeleccionado = marker;

		marker.setIcon(íconoAntenitaElegida);

		canalSeleccionado(null, null);

		FragmentManager fm = getFragmentManager();
		boolean yaEstaba = fm.findFragmentByTag("canales") != null;

		Antena antena = markerAAntena.get(marker);
		if(antena.canales == null)
		{
			if(yaEstaba)
				fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
			mapa.setPadding(0, altoActionBar, 0, 0);
			return false;
		}

		FragmentTransaction tr = fm.beginTransaction();
		if(yaEstaba)
			tr.setCustomAnimations(0, 0, R.anim.canales_enter, R.anim.canales_exit);
		else
			tr.setCustomAnimations(R.anim.canales_enter, R.anim.canales_exit, R.anim.canales_enter, R.anim.canales_exit);

		tr.replace(R.id.canales, CanalesMapaFragment.crear(antena), "canales")
				.addToBackStack("canales")
				.commit();

		return false;
	}

	public void canalSeleccionado(Antena antena, final Canal canal)
	{
		this.canalSeleccionado = canal;
		if(contornoActual != null)
		{
			contornoActual.remove();
			contornoActual = null;
		}
		if(tareaTraerContorno != null)
			tareaTraerContorno.cancel(false);
		if(antena == null || antena.país != País.US || canal == null || canal.ref == null)
			return;
		tareaTraerContorno = new AsyncTask<Void, Void, Polígono>()
		{
			@Override
			protected Polígono doInBackground(Void... params)
			{
				if(cachéDeContornos == null)
					cachéDeContornos = CachéDeContornos.dameInstancia(getActivity());
				if(isCancelled())
					return null;
				return cachéDeContornos.dameContornoFCC(Integer.parseInt(canal.ref));
			}

			@Override
			protected void onPostExecute(Polígono polygon)
			{
				tareaTraerContorno = null;
				if(polygon == null || canalSeleccionado != canal)
					return;
				Activity act = getActivity();
				if(act == null || Compat.activityIsDestroyed(act) || act.isFinishing())
					return;
				PolygonOptions poly = new PolygonOptions();
				poly.addAll(polygon.getPuntos());
				poly.fillColor(ContextCompat.getColor(act, R.color.contorno));
				poly.strokeWidth(getResources().getDimension(R.dimen.ancho_contorno));
				contornoActual = mapa.addPolygon(poly);
				CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(polygon.getBoundingBox(), (int)act.getResources().getDimension(R.dimen.paddingContorno));
				mapa.animateCamera(cameraUpdate);
			}

			@Override
			protected void onCancelled()
			{
				tareaTraerContorno = null;
			}
		};
		tareaTraerContorno.execute();
	}

	@Override
	public void onDestroyView()
	{
		/* Importante, porque...
		 * "Any objects obtained from the GoogleMap are associated with the view.
		 * It's important to not hold on to objects (e.g. Marker) beyond the view's life.
		 * Otherwise it will cause a memory leak as the view cannot be released."
		 */
		markerAAntena.clear();
		markerSeleccionado = null;
		países.clear();
		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		if(tareaTraerContorno != null)
			tareaTraerContorno.cancel(false);
		if(cachéDeContornos != null)
			cachéDeContornos.devolver();
		super.onDestroy();
	}
}
