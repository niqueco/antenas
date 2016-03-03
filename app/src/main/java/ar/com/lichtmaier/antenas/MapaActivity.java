package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
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
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.util.*;

public class MapaActivity extends AppCompatActivity
{
	private static BitmapDescriptor íconoAntenita, íconoAntenitaElegida;
	private Publicidad publicidad;

	private static final int PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION = 145;

	private long comienzoUsoPantalla;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);
		Toolbar tb = (Toolbar)findViewById(R.id.toolbar);
		if(tb != null)
		{
			setSupportActionBar(tb);
			//noinspection ConstantConditions
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		publicidad = new Publicidad(this, "ca-app-pub-0461170458442008/5727485755");

		if(savedInstanceState == null)
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MapaFragment())
					.commit();
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
	protected void onResume()
	{
		super.onResume();
		publicidad.onResume();
		comienzoUsoPantalla = System.currentTimeMillis();
	}

	@Override
	protected void onPause()
	{
		if(System.currentTimeMillis() - comienzoUsoPantalla > 1000 * 10)
			Calificame.registráQueMiróElMapa(this);
		publicidad.onPause();
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		publicidad.onDestroy();
		super.onDestroy();
	}

	@Override
	public void onBackPressed()
	{
		FragmentManager fm = getSupportFragmentManager();
		if(fm.getBackStackEntryCount() == 0)
		{
			super.onBackPressed();
			return;
		}
		fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
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

	public void canalSeleccionado(Antena antena, Canal canal)
	{
		MapaFragment mfr = (MapaFragment)getSupportFragmentManager().findFragmentById(R.id.container);
		mfr.canalSeleccionado(antena, canal);
	}

	public static class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener,
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

		public MapaFragment()
		{
		}

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
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			return inflater.inflate(R.layout.fragment_mapa, container, false);
		}

		@Override
		public void onActivityCreated(final Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			SupportMapFragment mapFragment = (SupportMapFragment)getChildFragmentManager().findFragmentById(R.id.map);
			MapCompat.getMapAsync(mapFragment, new MapCompat.OnMapReadyCallback()
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
				requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION);
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
			((MapaActivity)act).publicidad.load(loc);
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

		private void configurarPaddingMapa(ViewGroup v)
		{
			int height = 0;
			if(v != null)
			{
				boolean esTablet = v.getChildAt(0).getClass() == ScrollView.class;
				if(!esTablet)
					height = v.getHeight();
			}
			height -= ((MapaActivity)getActivity()).publicidad.getHeight();
			if(height < 0)
				height = 0;
			mapa.setPadding(0, altoActionBar, 0, height);
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

			new AsyncTask<Void, Void, List<FuturoMarcador>>() {

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

			tr.replace(R.id.canales, CanalesFragment.crear(antena), "canales")
				.addToBackStack("canales")
				.commit();

			return false;
		}

		public void canalSeleccionado(Antena antena, final Canal canal)
		{
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
					if(polygon == null)
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
		public void onDestroy()
		{
			if(tareaTraerContorno != null)
				tareaTraerContorno.cancel(false);
			if(cachéDeContornos != null)
				cachéDeContornos.devolver();
			super.onDestroy();
		}
	}

	public static class CanalesFragment extends Fragment
	{
		Antena antena;
		MapaActivity callback;
		private View selectedView;

		static CanalesFragment crear(Antena antena)
		{
			CanalesFragment fr = new CanalesFragment();
			Bundle args = new Bundle();
			args.putInt("país", antena.país.ordinal());
			args.putInt("index", antena.index);
			fr.setArguments(args);
			return fr;
		}

		@Override
		public void onAttach(Context context)
		{
			super.onAttach(context);
			callback = (MapaActivity)context;
		}

		private final View.OnClickListener canalClickListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if(selectedView != null)
				{
					selectedView.setSelected(false);
					//noinspection RedundantCast
					((FrameLayout)selectedView).setForeground(null);
				}
				selectedView = v;
				v.setSelected(true);
				//noinspection RedundantCast
				((FrameLayout)selectedView).setForeground(new ColorDrawable(0x55eeeeee));
				v.requestRectangleOnScreen(new Rect(0, 0, v.getWidth(), v.getHeight()));
				callback.canalSeleccionado(antena, (Canal)v.getTag());
			}
		};

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
		{
			País país = País.values()[getArguments().getInt("país")];
			int index = getArguments().getInt("index");
			antena = Antena.dameAntena(getActivity(), país, index);

			boolean hayImágenes = antena.hayImágenes();
			ContextThemeWrapper ctx = new ContextThemeWrapper(getActivity(), R.style.InfoMapa);
			final ViewGroup v = (ViewGroup)inflater.inflate(R.layout.info_mapa, container, false);
			TextView tv = (TextView)v.findViewById(R.id.antena_desc);
			if(tv != null)
			{
				if(antena.descripción == null)
					tv.setVisibility(View.GONE);
				else
					tv.setText(antena.descripción);
			}
			View viewCanalASeleccionar = null;
			final int canalSeleccionadoPos = savedInstanceState != null ? savedInstanceState.getInt("canal", -1) : -1;
			ViewGroup l = (ViewGroup)v.findViewById(R.id.lista_canales);
			int n;
			if(l instanceof TableLayout)
			{
				TypedArray arr = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.selectableItemBackground});
				int selectableItemBackground = arr.getResourceId(0, -1);
				arr.recycle();
				n = antena.canales.size();
				int ncolumns;
				if(l.getParent().getParent().getClass() != ScrollView.class)
				{
					int filas = n == 2 ? 1 : n > 6 && antena.descripción == null ? 3 : 2;

					ncolumns = (n + 1) / filas;
					if(ncolumns == 0)
						ncolumns++;
				} else
				{
					ncolumns = 1;
				}
				for(int i = 0; i < (n+ncolumns-1) / ncolumns; i++)
				{
					TableRow row = new TableRow(ctx);

					for(int j = 0 ; j < ncolumns && (i * ncolumns + j) < antena.canales.size() ; j++)
					{
						int posCanal = i * ncolumns + j;
						Canal canal = antena.canales.get(posCanal);
						View vc = canal.dameViewCanal(ctx, row, hayImágenes, false, false);

						FrameLayout fl = new FrameLayout(getContext());
						fl.addView(vc);
						vc = fl;

						if(antena.país == País.US && canal.ref != null)
						{
							if(viewCanalASeleccionar == null && (canalSeleccionadoPos == -1 || posCanal == canalSeleccionadoPos))
								viewCanalASeleccionar = vc;

							vc.setClickable(true);
							vc.setFocusable(true);
							vc.setTag(canal);
							//noinspection deprecation
							vc.setBackgroundResource(selectableItemBackground);
							vc.setOnClickListener(canalClickListener);
						}
						vc.setMinimumHeight((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()));
						if(j > 0)
							vc.setPadding((int)getResources().getDimension(vc.getPaddingLeft() + R.dimen.paddingColumnasInfoMapa), vc.getPaddingTop(), vc.getPaddingRight(), vc.getPaddingBottom());
						row.addView(vc);
					}

					l.addView(row);
				}
			} else
			{
				n = Math.min(antena.canales.size(), 4);
				for(Canal canal : antena.canales)
					l.addView(canal.dameViewCanal(ctx, l, hayImágenes, false, false));
			}
			if(n < antena.canales.size())
			{
				tv = new TextView(ctx);
				tv.setText(ctx.getString(R.string.some_more, antena.canales.size() - n));
				tv.setLayoutParams(
						(l instanceof TableLayout)
								? new TableLayout.LayoutParams()
								: new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
				);
				tv.setGravity(Gravity.CENTER);
				l.addView(tv);
			}
			ViewTreeObserver vto = v.getViewTreeObserver();
			vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					MapaFragment mfr = (MapaFragment)getFragmentManager().findFragmentById(R.id.container);

					if(mfr.mapa == null)
						return true;

					v.getViewTreeObserver().removeOnPreDrawListener(this);

					mfr.configurarPaddingMapa(v);

					return true;
				}
			});
			if(viewCanalASeleccionar != null)
				canalClickListener.onClick(viewCanalASeleccionar);
			return v;
		}

		@Override
		public void onSaveInstanceState(Bundle outState)
		{
			super.onSaveInstanceState(outState);

			if(selectedView != null)
				//noinspection SuspiciousMethodCalls
				outState.putInt("canal", antena.canales.indexOf(selectedView.getTag()));

		}
	}
}
