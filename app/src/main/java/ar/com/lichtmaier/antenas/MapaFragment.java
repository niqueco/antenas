package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.*;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.*;
import android.widget.ScrollView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.*;
import java.util.concurrent.TimeoutException;

public class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener,
		GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMapClickListener,
		GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraIdleListener,
		GoogleMap.OnMarkerClickListener, GoogleMap.OnPolylineClickListener
{
	private GoogleMap mapa;

	private final Map<País, List<Marker>> países = new EnumMap<>(País.class);

	private Antena antenaSeleccionada;
	private Marker markerSeleccionado;
	private CachéDeContornos cachéDeContornos;
	private Polygon contornoActual;
	private int altoActionBar;
	final private EnumSet<País> paísesPrendidos = EnumSet.noneOf(País.class);
	private LiveData<Polígono> contornoLiveData;
	private int originalBackStackEntryCount;
	private Publicidad publicidad;
	private SharedPreferences prefs;

	private static BitmapDescriptor íconoAntenita, íconoAntenitaElegida;
	private static final int PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION = 145;
	private Canal canalSeleccionado;
	private double latitudActual, longitudActual;
	final private Map<Antena, Polyline> líneas = new HashMap<>();
	final private Map<Antena, Marker> antenaAMarker = new HashMap<>();
	private boolean dibujandoLíneas;
	private boolean markersCargados;
	private boolean mapaMovido;
	private boolean huboEjecuciónPrevia;

	/** Estamos mostrando un canal porque cliquearon en la lista de antenas. Un back debería cerrar la actividad sin miramientos. */
	boolean modoMostrarAntena;
	private Marker markerLugar;
	private boolean myLocationEnabled;
	private boolean mapaSatelital;
	private MenuItem tipoMapaMenúItem;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		FragmentActivity activity = requireActivity();

		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		FragmentManager fm = requireFragmentManager();

		setHasOptionsMenu(true);

		huboEjecuciónPrevia = savedInstanceState != null;
		if(huboEjecuciónPrevia)
		{
			originalBackStackEntryCount = savedInstanceState.getInt("originalBackStackEntryCount");
			antenaSeleccionada = savedInstanceState.getParcelable("antenaSeleccionada");
			mapaMovido = savedInstanceState.getBoolean("mapaMovido");
			modoMostrarAntena = savedInstanceState.getBoolean("modoMostrarAntena");
			setMapaSatelital(savedInstanceState.getBoolean("mapaSatelital"));
		} else
		{
			originalBackStackEntryCount = fm.getBackStackEntryCount();

			Intent intent = activity.getIntent();
			Antena.applicationContext = activity.getApplicationContext();
			antenaSeleccionada = intent.getParcelableExtra("ar.com.lichtmaier.antenas.antena");
			Antena.applicationContext = null;
			if(antenaSeleccionada != null)
			{
				if(antenaSeleccionada.canales != null)
				{
					int canalPos = intent.getIntExtra("ar.com.lichtmaier.antenas.canal", -1);
					if(canalPos != -1)
					{
						canalSeleccionado = antenaSeleccionada.canales.get(canalPos);
						modoMostrarAntena = true;
					}
				}
			}
		}

		fm.addOnBackStackChangedListener(() -> {
			if(requireFragmentManager().getBackStackEntryCount() == originalBackStackEntryCount)
			{
				canalSeleccionado(null, null);
				if(markerSeleccionado != null)
				{
					markerSeleccionado.hideInfoWindow();
					markerSeleccionado.setIcon(íconoAntenita);
					estiloLínea(antenaSeleccionada, false);
					markerSeleccionado = null;
					antenaSeleccionada = null;
				}
				mapa.setPadding(0, altoActionBar, 0, 0);
			}
		});

		AyudanteDePagos.dameInstancia(getContext()).observe(this, this::esPro);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt("originalBackStackEntryCount", originalBackStackEntryCount);
		if(antenaSeleccionada != null)
			outState.putParcelable("antenaSeleccionada", antenaSeleccionada);
		outState.putBoolean("mapaMovido", mapaMovido);
		outState.putBoolean("modoMostrarAntena", modoMostrarAntena);
		outState.putBoolean("mapaSatelital", mapaSatelital);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_mapa, container, false);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		SupportMapFragment mapFragment = (SupportMapFragment)getChildFragmentManager().findFragmentById(R.id.map);
		mapFragment.getMapAsync(googleMap -> {
			mapa = googleMap;
			inicializarMapa(savedInstanceState);
		});
		MapaActivity activity = (MapaActivity)getActivity();
		if(activity == null)
		{
			logFragmentStatus();
			Crashlytics.log("fragment status: detached: " + isDetached() + ", hidden: " + isHidden() + ", added: " + isAdded() + ", removing: " + isRemoving());
			Crashlytics.logException(new RuntimeException("getActivity es null"));
			return;
		}
		LiveData<Location> location = activity.getLocation();
		location.observe(this, this::onLocationChanged);
	}

	private void inicializarMapa(Bundle savedInstanceState)
	{
		boolean hayPermiso = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
		if(!hayPermiso)
			requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION);
		FragmentActivity act = getActivity();
		mapa.setMyLocationEnabled(hayPermiso);
		myLocationEnabled = hayPermiso;
		mapa.setIndoorEnabled(false);
		if(savedInstanceState == null)
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
		mapa.setOnInfoWindowClickListener(this);
		mapa.setOnMapClickListener(this);
		mapa.setOnCameraMoveListener(this);
		mapa.setOnCameraIdleListener(this);
		mapa.setOnMarkerClickListener(this);
		mapa.setOnPolylineClickListener(this);
		configurarTipoMapa();
		Lugar l = Lugar.actual.getValue();
		if(l != null)
		{
			LatLng latLng = new LatLng(l.coords.getLatitude(), l.coords.getLongitude());
			markerLugar = mapa.addMarker(new MarkerOptions().position(latLng).title(l.name));
		}
		if(!huboEjecuciónPrevia && antenaSeleccionada != null)
		{
			mapa.moveCamera(CameraUpdateFactory.newLatLng(antenaSeleccionada.getLatLng()));
			mapaMovido = true;
		} else if(l != null)
		{
			if(savedInstanceState == null)
			{
				LatLng latLng = new LatLng(l.coords.getLatitude(), l.coords.getLongitude());
				mapa.moveCamera(CameraUpdateFactory.newLatLng(latLng));
				mapaMovido = true;
			}
		} else if(AntenaActivity.coordsUsuario != null)
		{
			if(savedInstanceState == null)
			{
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
				mapaMovido = true;
			}
		}
		if(íconoAntenita == null)
			íconoAntenita = BitmapDescriptorFactory.fromResource(R.drawable.antena);
		if(íconoAntenitaElegida == null)
			íconoAntenitaElegida = BitmapDescriptorFactory.fromResource(R.drawable.antena_seleccionada);
		prefs.registerOnSharedPreferenceChangeListener(this);
		for(País país : País.values())
			if(prefs.getBoolean("mapa_país_" + país, false))
			{
				Antena.dameAntenas(act, país);
				paísesPrendidos.add(país);
			}
		ActionBar actionBar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
		if(actionBar != null)
			altoActionBar = actionBar.getHeight();

		if(altoActionBar == 0)
		{
			View v = getView();
			if(v == null)
				throw new RuntimeException("uh?");
			v.post(() -> {
				AppCompatActivity activity1 = (AppCompatActivity)getActivity();
				if(activity1 == null)
					return;
				ActionBar actionBar1 = activity1.getSupportActionBar();
				if(actionBar1 != null)
					altoActionBar = actionBar1.getHeight();
				configurarPaddingMapa();
			});
		} else
		{
			configurarPaddingMapa();
		}

		dibujarLíneas(prefs.getBoolean("dibujar_líneas", true));
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION)
		{
			if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
				mapa.setMyLocationEnabled(true);
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if(item.getItemId() == R.id.action_tipo_mapa)
		{
			setMapaSatelital(!this.mapaSatelital);
			Context context = getContext();
			if(context != null)
				FirebaseAnalytics.getInstance(context).logEvent(mapaSatelital ? "mapa_satelital" :  "mapa_fisico", null);
			return true;
		} else
			return super.onOptionsItemSelected(item);
	}

	private void setMapaSatelital(boolean ms)
	{
		if(ms == mapaSatelital)
			return;
		mapaSatelital = ms;
		if(mapa != null)
		{
			configurarTipoMapa();
		}
		if(tipoMapaMenúItem != null)
			configurarMenúItems();
	}

	private void configurarTipoMapa()
	{
		mapa.setMapType(mapaSatelital ? GoogleMap.MAP_TYPE_HYBRID : GoogleMap.MAP_TYPE_TERRAIN);
	}

	private void configurarMenúItems()
	{
		tipoMapaMenúItem.setIcon(mapaSatelital ? R.drawable.ic_terrain_white_24dp : R.drawable.ic_satellite_white_24dp);
		tipoMapaMenúItem.setTitle(mapaSatelital ? getString(R.string.mapa_físico) : getString(R.string.mapa_satelital));
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.mapa_fragment, menu);
		tipoMapaMenúItem = menu.findItem(R.id.action_tipo_mapa);
		configurarMenúItems();
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
				if(antenaSeleccionada != null && antenaSeleccionada.país == país)
				{
					antenaSeleccionada = null;
					markerSeleccionado = null;
				}
				List<Marker> markers = países.remove(país);
				if(markers != null)
					for(Marker marker : markers)
					{
						Antena antena = (Antena)marker.getTag();
						antenasDentro.remove(antena);
						marker.remove();
						Polyline polyline = líneas.remove(antena);
						if(polyline != null)
							polyline.remove();
					}
				paísesPrendidos.remove(país);
			}
			actualizarLíneas(false);
		} else if(key.equals("max_dist"))
		{
			actualizarLíneas(true);
		} else if(key.equals("dibujar_líneas"))
		{
			dibujarLíneas(sharedPreferences.getBoolean("dibujar_líneas", true));
		}
	}

	private void configurarPaddingMapa()
	{
		Fragment frCanales = requireFragmentManager().findFragmentByTag("canales");
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
		if(publicidad != null)
			height -= publicidad.getHeight();
		if(height < 0)
			height = 0;
		mapa.setPadding(0, altoActionBar, 0, height);
	}

	public boolean mapaInicializado()
	{
		return mapa != null;
	}

	@SuppressLint("MissingPermission")
	private void onLocationChanged(Location location)
	{
		if(publicidad != null)
			publicidad.load(location);

		latitudActual = location.getLatitude();
		longitudActual = location.getLongitude();

		if(mapa == null)
			return; // El mapa todavía no se terminó de inicializar.

		if(!myLocationEnabled)
			mapa.setMyLocationEnabled(true);

		if(!mapaMovido)
		{
			mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(latitudActual, longitudActual)));
			mapaMovido = true;
		}

		actualizarLíneas(false);
	}

	private void esPro(Boolean pro)
	{
		Log.i("antenas", "mapa: es pro: " + pro);
		if(pro == null)
			return;
		if(pro)
		{
			if(publicidad == null)
				return;
			publicidad.sacar();
			publicidad = null;
			configurarPaddingMapa();
		} else
		{
			if(publicidad != null)
				return;
			FragmentActivity activity = getActivity();
			if(activity == null)
				return;
			publicidad = new Publicidad(activity, getLifecycle(), "ca-app-pub-0461170458442008/5727485755");
			if(mapa != null)
				configurarPaddingMapa();
		}
	}

	private static class FuturoMarcador
	{
		final Antena antena;
		final MarkerOptions markerOptions;

		FuturoMarcador(Antena antena, Context context)
		{
			this.antena = antena;
			this.markerOptions = new MarkerOptions()
					.position(antena.getLatLng())
					.title((antena.canales == null || antena.canales.isEmpty()) ? antena.dameNombre(context) : null)
					.icon(íconoAntenita);
		}

		private void crear(MapaFragment mapaFragment)
		{
			Marker marker = mapaFragment.mapa.addMarker(markerOptions);
			marker.setTag(antena);

			List<Marker> markers = mapaFragment.países.get(antena.país);
			if(markers == null)
			{
				markers = new ArrayList<>();
				mapaFragment.países.put(antena.país, markers);
			}
			markers.add(marker);

			mapaFragment.antenaAMarker.put(antena, marker);
			mapaFragment.markersCargados = true;
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

					mm.add(new FuturoMarcador(antena, getActivity()));
				}
				return mm;
			}

			@Override
			protected void onPostExecute(List<FuturoMarcador> mm)
			{
				if(!isAdded())
				{
					Log.e("antenas", "setting markers at a wong time? fr=" + MapaFragment.this);
					return;
				}

				for(FuturoMarcador m : mm)
					m.crear(MapaFragment.this);

				if(markerSeleccionado == null && antenaSeleccionada != null)
				{
					markerSeleccionado = antenaAMarker.get(antenaSeleccionada);
					if(markerSeleccionado == null)
					{
						Log.e("antenas", "La antena seleccionada " + antenaSeleccionada + " no tiene marker?");
						return;
					}

					if(huboEjecuciónPrevia)
					{
						markerSeleccionado.setIcon(íconoAntenitaElegida);
						estiloLínea(antenaSeleccionada, true);
					} else
					{
						try {
							onMarkerClick(markerSeleccionado);
						} catch(IllegalStateException e)
						{
							Crashlytics.log("activity: " + getActivity());
							logFragmentStatus();
							Crashlytics.logException(e);
						}
					}
				}
			}

		}.execute();
	}

	final private Set<Antena> antenasDentro = new HashSet<>();

	@Override
	public void onCameraMove()
	{
		ponerMarcadores();
	}

	@Override
	public void onCameraIdle()
	{
		ponerMarcadores();
	}

	@Override
	public void onInfoWindowClick(Marker marker)
	{
		Antena antena = (Antena)marker.getTag();
		//noinspection ConstantConditions
		antena.mostrarInformacion(getActivity());
	}

	@Override
	public void onMapClick(LatLng latLng)
	{
		if(isStateSaved())
			return;

		// Esto se hace igual al hacer pop del fragmento de la info,
		// pero por si no hay info también se hace acá.
		if(markerSeleccionado != null)
		{
			markerSeleccionado.hideInfoWindow();
			markerSeleccionado.setIcon(íconoAntenita);
			estiloLínea((Antena)markerSeleccionado.getTag(), false);
			markerSeleccionado = null;
			antenaSeleccionada = null;
		}
		canalSeleccionado(null, null);
		FragmentManager fm = getFragmentManager();
		if(fm == null)
		{
			logFragmentStatus();
			Crashlytics.logException(new NullPointerException("fm es null!"));
		} else if(fm.findFragmentByTag("canales") != null)
			fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
		mapa.setPadding(0, altoActionBar, 0, 0);
		modoMostrarAntena = false;
	}

	@Override
	public boolean onMarkerClick(Marker marker)
	{
		if(marker.equals(markerLugar))
			return false;

		FragmentManager fm = getFragmentManager();

		if(fm == null || isStateSaved())
			return true;

		if(markerSeleccionado != null && marker != markerSeleccionado)
		{
			markerSeleccionado.setIcon(íconoAntenita);
			estiloLínea(antenaSeleccionada, false);
		}

		markerSeleccionado = marker;
		antenaSeleccionada = (Antena)marker.getTag();

		marker.setIcon(íconoAntenitaElegida);

		boolean yaEstaba = fm.findFragmentByTag("canales") != null;

		Antena antena = (Antena)marker.getTag();
		estiloLínea(antena, true);

		//noinspection ConstantConditions
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

		CanalesMapaFragment fr = CanalesMapaFragment.crear(antena, canalSeleccionado == null || antenaSeleccionada.canales == null || !antenaSeleccionada.canales.contains(canalSeleccionado) ? null : canalSeleccionado);
		tr.replace(R.id.canales, fr, "canales")
				.addToBackStack("canales")
				.commit();

		return false;
	}

	private void estiloLínea(Antena antena, boolean sel)
	{
		Polyline línea = líneas.get(antena);
		Log.d("antenas", "estiloLínea " + antena + ", sel=" +sel + ", línea="+línea);
		if(línea != null)
		{
			Context context = requireContext();
			int color = ContextCompat.getColor(context, sel ? R.color.línea_mapa_sel : R.color.línea_mapa);
			float ancho = context.getResources().getDimension(sel ? R.dimen.ancho_línea_antena_sel : R.dimen.ancho_línea_antena);

			PropertyValuesHolder h1 = PropertyValuesHolder.ofFloat("width", línea.getWidth(), ancho);
			PropertyValuesHolder h2 = PropertyValuesHolder.ofInt("color", línea.getColor(), color);
			h2.setEvaluator(new ArgbEvaluator());
			ValueAnimator animator = ObjectAnimator.ofPropertyValuesHolder(línea, h1, h2);
			animator.setDuration(150);
			animator.start();
		}
	}

	public void canalSeleccionado(Antena antena, final Canal canal)
	{
		this.canalSeleccionado = canal;
		if(contornoActual != null)
		{
			contornoActual.remove();
			contornoActual = null;
		}
		if(contornoLiveData != null)
			contornoLiveData.removeObservers(this);
		if(antena == null || antena.país != País.US || canal == null || canal.ref == null)
			return;
		if(cachéDeContornos == null)
			cachéDeContornos = CachéDeContornos.dameInstancia(getActivity());

		contornoLiveData = cachéDeContornos.dameContornoFCC_LD(canal.ref);
		contornoLiveData.observe(this, new Observer<Polígono>()
		{
			@Override
			public void onChanged(@Nullable Polígono polygon)
			{
				contornoLiveData.removeObserver(this);
				contornoLiveData = null;
				if(polygon == null || canalSeleccionado != canal)
					return;
				Activity act = MapaFragment.this.getActivity();
				if(act == null || !MapaFragment.this.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.INITIALIZED))
					return;
				PolygonOptions poly = new PolygonOptions();
				poly.addAll(polygon.getPuntos());
				poly.fillColor(ContextCompat.getColor(act, R.color.contorno));
				poly.strokeWidth(MapaFragment.this.getResources().getDimension(R.dimen.ancho_contorno));
				contornoActual = mapa.addPolygon(poly);
				View view = MapaFragment.this.getView();
				if(view != null)
				{
					CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(polygon.getBoundingBox(), view.getWidth(), view.getHeight(), (int)act.getResources().getDimension(R.dimen.paddingContorno));
					try
					{
						mapa.animateCamera(cameraUpdate);
					} catch(Exception e)
					{
						MapaFragment.this.logFragmentStatus();
						Crashlytics.logException(e);
					}
				}
			}
		});
	}

	@Override
	public void onDestroyView()
	{
		/* Importante, porque...
		 * "Any objects obtained from the GoogleMap are associated with the view.
		 * It's important to not hold on to objects (e.g. Marker) beyond the view's life.
		 * Otherwise it will cause a memory leak as the view cannot be released."
		 */
		markerSeleccionado = null;
		países.clear();
		líneas.clear();
		antenaAMarker.clear();
		markerLugar = null;
		super.onDestroyView();
	}

	@Override
	public void onDestroy()
	{
		if(cachéDeContornos != null)
			cachéDeContornos.devolver();
		super.onDestroy();
	}

	/** Prende y apaga el dibujado de líneas. */
	private void dibujarLíneas(boolean dibujarLíneas)
	{
		if(dibujarLíneas == dibujandoLíneas)
			return;
		this.dibujandoLíneas = dibujarLíneas;
		if(!dibujarLíneas)
		{
			for(Polyline p : líneas.values())
				p.remove();
			líneas.clear();
		} else
		{
			actualizarLíneas(false);
		}
	}

	private Set<Antena> antenasCerca;
	private long últimaVezQueSeBuscóAntenas;
	private final static Cap ROUND_CAP = new RoundCap();

	private void actualizarLíneas(boolean forzarBusqueda)
	{
		if(!dibujandoLíneas || !isVisible())
			return;
		int maxDist = Math.min(Integer.parseInt(prefs.getString("max_dist", "60")), 100) * 1000;

		Lugar l = Lugar.actual.getValue();

		if(forzarBusqueda || antenasCerca == null || (System.nanoTime() - últimaVezQueSeBuscóAntenas) > 1000000000L * 60)
		{
			try
			{
				antenasCerca = new HashSet<>(Antena.dameAntenasCerca(getActivity(), l == null ? new GlobalCoordinates(latitudActual, longitudActual) : new GlobalCoordinates(l.coords.getLatitude(), l.coords.getLongitude()), maxDist, false, 4000000));
			} catch(TimeoutException e)
			{
				Log.w("antenas", "No hay antenas todavía para hacer líneas");
				return;
			}
			últimaVezQueSeBuscóAntenas = System.nanoTime();
			Log.i("antenas", "Tengo " + antenasCerca.size() + " antenas para hacer líneas.");
		}

		LatLng posNosotros = l == null ? new LatLng(latitudActual, longitudActual) : new LatLng(l.coords.getLatitude(), l.coords.getLongitude());

		Iterator<Antena> itA = antenasCerca.iterator();
		while(itA.hasNext())
		{
			Antena antena = itA.next();

			if(!paísesPrendidos.contains(antena.país))
			{
				itA.remove();
				continue;
			}

			if(cachéDeContornos == null)
				cachéDeContornos = CachéDeContornos.dameInstancia(getActivity());

			if(!cachéDeContornos.enContorno(antena, posNosotros, false))
			{
				if(Log.isLoggable("antenas", Log.DEBUG))
					Log.d("antenas", "No se dibuja línea de antena " + antena + " porque estamos fuera de su contorno de alcance.");
				itA.remove();
				continue;
			}

			Polyline polyline = líneas.get(antena);
			if(polyline != null)
			{
				//Log.d("antenas", "modifico "+polyline);
				List<LatLng> points = new ArrayList<>(2);
				points.add(posNosotros);
				points.add(antena.getLatLng());
				polyline.setPoints(points);
			} else
			{
				if(!antenasDentro.contains(antena))
					new FuturoMarcador(antena, getContext()).crear(this);
				boolean sel = antena == antenaSeleccionada;
				polyline = mapa.addPolyline(new PolylineOptions()
						.add(posNosotros, antena.getLatLng())
						.geodesic(true)
						.width(getResources().getDimension(sel
								? R.dimen.ancho_línea_antena_sel
								: R.dimen.ancho_línea_antena))
						.color(ContextCompat.getColor(requireContext(), sel
								? R.color.línea_mapa_sel
								: R.color.línea_mapa))
						.endCap(ROUND_CAP));
				polyline.setTag(antena);
				polyline.setClickable(true);
				//Log.d("antenas", "agrego "+polyline);
				líneas.put(antena, polyline);
			}
		}

		Iterator<Map.Entry<Antena, Polyline>> itL = líneas.entrySet().iterator();
		while(itL.hasNext())
		{
			Map.Entry<Antena, Polyline> e = itL.next();
			if(!antenasCerca.contains(e.getKey()))
			{
				e.getValue().remove();
				itL.remove();
			}
		}
	}

	@Override
	public void onPolylineClick(Polyline polyline)
	{
		Log.d("antenas", "click en " + polyline);
		Antena antena = (Antena)polyline.getTag();
		if(antena == null)
		{
			Crashlytics.log("lineas: " + líneas);
			Crashlytics.log("polyline: " + polyline);
			logFragmentStatus();
			Crashlytics.logException(new RuntimeException("onPolylineClick: unknown polyline"));
			return;
		}
		Log.d("antenas", "click en línea de " + antena);

		Marker marker = antenaAMarker.get(antena);
		if(marker == null)
		{
			logFragmentStatus();
			Crashlytics.logException(new NullPointerException("la antena " + antena + " no tiene marker?? antenas: " + antenaAMarker.keySet()));
			return;
		}

		if(!markersCargados)
		{
			antenaSeleccionada = antena;
			return;
		}

		onMarkerClick(marker);
	}

	private void logFragmentStatus()
	{
		Crashlytics.log("antenaSeleccionada: " + antenaSeleccionada);
		Crashlytics.log("markerSeleccionado: " + markerSeleccionado);
		Crashlytics.log("canalSeleccionado: " + canalSeleccionado);
		Crashlytics.log("estado en el ciclo de vida: " + getLifecycle().getCurrentState());
	}
}
