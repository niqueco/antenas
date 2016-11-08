package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.*;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

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
	private AsyncTask<Void, Void, Polígono> tareaTraerContorno;
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

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		huboEjecuciónPrevia = savedInstanceState != null;
		if(huboEjecuciónPrevia)
		{
			originalBackStackEntryCount = savedInstanceState.getInt("originalBackStackEntryCount");
			antenaSeleccionada = savedInstanceState.getParcelable("antenaSeleccionada");
			mapaMovido = savedInstanceState.getBoolean("mapaMovido");
			modoMostrarAntena = savedInstanceState.getBoolean("modoMostrarAntena");
		} else
		{
			originalBackStackEntryCount = getFragmentManager().getBackStackEntryCount();

			Intent intent = getActivity().getIntent();
			antenaSeleccionada = intent.getParcelableExtra("ar.com.lichtmaier.antenas.antena");
			if(antenaSeleccionada != null)
			{
				canalSeleccionado = antenaSeleccionada.canales.get(intent.getIntExtra("ar.com.lichtmaier.antenas.canal", -1));
				modoMostrarAntena = true;
			}
		}

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
						estiloLínea(antenaSeleccionada, false);
						markerSeleccionado = null;
						antenaSeleccionada = null;
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
		if(antenaSeleccionada != null)
			outState.putParcelable("antenaSeleccionada", antenaSeleccionada);
		outState.putBoolean("mapaMovido", mapaMovido);
		outState.putBoolean("modoMostrarAntena", modoMostrarAntena);
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
		mapa.setIndoorEnabled(false);
		if(savedInstanceState == null)
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
		mapa.setOnInfoWindowClickListener(this);
		mapa.setOnMapClickListener(this);
		mapa.setOnCameraMoveListener(this);
		mapa.setOnCameraIdleListener(this);
		mapa.setOnMarkerClickListener(this);
		mapa.setOnPolylineClickListener(this);
		Location loc = null;
		if(AntenaActivity.coordsUsuario != null)
		{
			if(savedInstanceState == null)
			{
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
				mapaMovido = true;
			}
			loc = new Location("*");
			loc.setLatitude(AntenaActivity.coordsUsuario.getLatitude());
			loc.setLongitude(AntenaActivity.coordsUsuario.getLongitude());
		}
		publicidad.load(loc);
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

		dibujarLíneas(prefs.getBoolean("dibujar_líneas", true));
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
			actualizarLíneas();
		} else if(key.equals("max_dist"))
		{
			actualizarLíneas();
		} else if(key.equals("dibujar_líneas"))
		{
			dibujarLíneas(sharedPreferences.getBoolean("dibujar_líneas", true));
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

	public void onLocationChanged(Location location)
	{
		latitudActual = location.getLatitude();
		longitudActual = location.getLongitude();

		if(!mapaMovido)
		{
			mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(latitudActual, longitudActual)));
			mapaMovido = true;
		}

		actualizarLíneas();
	}

	static class FuturoMarcador
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
						if(!mapa.getProjection().getVisibleRegion().latLngBounds.contains(markerSeleccionado.getPosition()))
						{
							CameraUpdate u = CameraUpdateFactory.newLatLng(markerSeleccionado.getPosition());
							mapa.moveCamera(u);
						}
						onMarkerClick(markerSeleccionado);
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
			estiloLínea((Antena)markerSeleccionado.getTag(), false);
			markerSeleccionado = null;
			antenaSeleccionada = null;
		}
		canalSeleccionado(null, null);
		FragmentManager fm = getFragmentManager();
		if(fm.findFragmentByTag("canales") != null)
			fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
		mapa.setPadding(0, altoActionBar, 0, 0);
		modoMostrarAntena = false;
	}

	@Override
	public boolean onMarkerClick(Marker marker)
	{
		if(markerSeleccionado != null && marker != markerSeleccionado)
		{
			markerSeleccionado.setIcon(íconoAntenita);
			estiloLínea(antenaSeleccionada, false);
		}

		markerSeleccionado = marker;
		antenaSeleccionada = (Antena)marker.getTag();

		marker.setIcon(íconoAntenitaElegida);

		FragmentManager fm = getFragmentManager();
		boolean yaEstaba = fm.findFragmentByTag("canales") != null;

		Antena antena = (Antena)marker.getTag();
		estiloLínea(antena, true);

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

		CanalesMapaFragment fr = CanalesMapaFragment.crear(antena, canalSeleccionado == null || !antenaSeleccionada.canales.contains(canalSeleccionado) ? null : canalSeleccionado);
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
			int color = ContextCompat.getColor(getActivity(), sel ? R.color.línea_mapa_sel : R.color.línea_mapa);
			float ancho = getActivity().getResources().getDimension(sel ? R.dimen.ancho_línea_antena_sel : R.dimen.ancho_línea_antena);

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			{
				PropertyValuesHolder h1 = PropertyValuesHolder.ofFloat("width", línea.getWidth(), ancho);
				PropertyValuesHolder h2 = PropertyValuesHolder.ofInt("color", línea.getColor(), color);
				h2.setEvaluator(new ArgbEvaluator());
				ValueAnimator animator = ObjectAnimator.ofPropertyValuesHolder(línea, h1, h2);
				animator.setDuration(150);
				animator.start();
			} else
			{
				línea.setColor(color);
				línea.setWidth(ancho);
			}
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
				return cachéDeContornos.dameContornoFCC(canal.ref);
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
		markerSeleccionado = null;
		países.clear();
		líneas.clear();
		antenaAMarker.clear();
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
		}
	}

	private Set<Antena> antenasCerca;
	private long últimaVezQueSeBuscóAntenas;

	private void actualizarLíneas()
	{
		if(!dibujandoLíneas || !isVisible())
			return;
		int maxDist = Math.min(Integer.parseInt(prefs.getString("max_dist", "60")), 100) * 1000;

		if(antenasCerca == null || (System.nanoTime() - últimaVezQueSeBuscóAntenas) > 1000000000L * 60)
		{
			try
			{
				antenasCerca = new HashSet<>(Antena.dameAntenasCerca(getActivity(), new GlobalCoordinates(latitudActual, longitudActual), maxDist, false));
			} catch(TimeoutException e)
			{
				Log.w("antenas", "No hay antenas todavía para hacer líneas");
				return;
			}
			últimaVezQueSeBuscóAntenas = System.nanoTime();
			Log.i("antenas", "Tengo " + antenasCerca.size() + " antenas para hacer líneas.");
		}

		LatLng posNosotros = new LatLng(latitudActual, longitudActual);

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
						.width(getActivity().getResources().getDimension(sel
								? R.dimen.ancho_línea_antena_sel
								: R.dimen.ancho_línea_antena))
						.color(ContextCompat.getColor(getActivity(), sel
								? R.color.línea_mapa_sel
								: R.color.línea_mapa)));
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
		Antena antena = null;
		for(Map.Entry<Antena, Polyline> e : líneas.entrySet())
			if(polyline.equals(e.getValue()))
			{
				antena = e.getKey();
				break;
			}
		if(antena == null)
			throw new RuntimeException("uh? " + polyline);
		Log.d("antenas", "click en línea de " + antena);

		if(!markersCargados)
		{
			antenaSeleccionada = antena;
			return;
		}

		Marker marker = antenaAMarker.get(antena);
		if(marker == null)
			throw new NullPointerException("la antena " + antena + "no tiene marker?? antenas: " + antenaAMarker.keySet());
		onMarkerClick(marker);
	}
}
