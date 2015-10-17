package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
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
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;

import java.util.*;

public class MapaActivity extends AppCompatActivity
{
	private static BitmapDescriptor íconoAntenita;
	private Publicidad publicidad;

	private static final int PEDIDO_DE_PERMISO_WRITE_EXTERNAL_STORAGE = 144;
	private boolean seMuestraRuegoDePermisos;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);
		Toolbar tb = (Toolbar)findViewById(R.id.toolbar);
		if(tb != null)
		{
			setSupportActionBar(tb);
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		publicidad = new Publicidad(this, "ca-app-pub-0461170458442008/5727485755");

		if(savedInstanceState == null)
		{
			if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			{
				if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
				{
					ViewGroup container = (ViewGroup)findViewById(R.id.container);
					View view = getLayoutInflater().inflate(R.layout.permiso_necesario, container, false);
					((TextView)view.findViewById(android.R.id.text1)).setText(R.string.explicacion_permiso_almacenamiento);
					view.findViewById(R.id.button).setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							ActivityCompat.requestPermissions(MapaActivity.this,
								new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE},
								PEDIDO_DE_PERMISO_WRITE_EXTERNAL_STORAGE);
						}
					});
					container.addView(view);
					seMuestraRuegoDePermisos = true;
				}
				if(!seMuestraRuegoDePermisos)
				{
					ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, PEDIDO_DE_PERMISO_WRITE_EXTERNAL_STORAGE);
				}
			} else
			{
				getSupportFragmentManager().beginTransaction()
						.add(R.id.container, new MapaFragment())
						.commit();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_WRITE_EXTERNAL_STORAGE)
		{
			if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				if(seMuestraRuegoDePermisos)
					((ViewGroup)findViewById(R.id.container)).removeAllViews();
				getSupportFragmentManager().beginTransaction()
						.add(R.id.container, new MapaFragment())
						.commit();
			} else
				finish();
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
	}

	@Override
	protected void onPause()
	{
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

	public static class MapaFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener,
			GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMapClickListener,
			GoogleMap.OnCameraChangeListener, GoogleMap.OnMarkerClickListener, CanalesFragment.Callback
	{
		private GoogleMap mapa;

		private final Map<País, List<Marker>> países = new EnumMap<>(País.class);

		private final Map<Marker, Antena> markerAAntena = new HashMap<>();
		private Marker marker;
		private CachéDeContornos cachéDeContornos;
		private Polygon contornoActual;
		private int altoActionBar;

		public MapaFragment()
		{
		}

		@Override
		public void onCreate(@Nullable Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			final int n = getFragmentManager().getBackStackEntryCount();

			getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener()
			{
				@Override
				public void onBackStackChanged()
				{
					if(getFragmentManager().getBackStackEntryCount() == n)
					{
						canalSeleccionado(null, null);
						marker.hideInfoWindow();
						mapa.setPadding(0, altoActionBar, 0, 0);
					}
				}
			});
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
			mapa.setOnMapClickListener(this);
			mapa.setOnCameraChangeListener(this);
			mapa.setOnMarkerClickListener(this);
			Location loc = null;
			if(AntenaActivity.coordsUsuario != null)
			{
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
				loc = new Location("*");
				loc.setLatitude(AntenaActivity.coordsUsuario.getLatitude());
				loc.setLongitude(AntenaActivity.coordsUsuario.getLongitude());
			}
			((MapaActivity)act).publicidad.load(loc);
			if(íconoAntenita == null)
				íconoAntenita = BitmapDescriptorFactory.fromResource(R.drawable.antena);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
			prefs.registerOnSharedPreferenceChangeListener(this);
			for(País país : País.values())
				if(prefs.getBoolean("mapa_país_" + país, false))
					Antena.dameAntenas(act, país);
			act.findViewById(R.id.map).post(new Runnable() {
				@Override
				public void run()
				{
					AppCompatActivity activity = (AppCompatActivity)getActivity();
					if(activity == null)
						return;
					ActionBar actionBar = activity.getSupportActionBar();
					if(actionBar == null)
						return;
					altoActionBar = actionBar.getHeight();
					mapa.setPadding(0, altoActionBar, 0, 0);
				}
			});
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
				} else
				{
					List<Marker> markers = países.remove(país);
					if(markers != null)
						for(Marker marker : markers)
						{
							antenasDentro.remove(markerAAntena.get(marker));
							marker.remove();
						}
				}
			}
		}

		private void ponerMarcadores()
		{
			Activity act = getActivity();
			if(act == null)
				return;
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(act);
			LatLngBounds latLngBounds = mapa.getProjection().getVisibleRegion().latLngBounds;
			antenas_temp.clear();
			Antena.antenasEnRectángulo(latLngBounds.northeast.latitude,
					latLngBounds.southwest.longitude,
					latLngBounds.southwest.latitude,
					latLngBounds.northeast.longitude,
					antenas_temp);
			for(Antena antena : antenas_temp)
			{
				if(!prefs.getBoolean("mapa_país_" + antena.país, false))
					continue;
				if(!antenasDentro.add(antena))
					continue;

				Marker marker = mapa.addMarker(new MarkerOptions()
						.position(antena.getLatLng())
						.title(antena.dameNombre(getActivity()))
						.icon(íconoAntenita));
				markerAAntena.put(marker, antena);

				List<Marker> markers = países.get(antena.país);
				if(markers == null)
				{
					markers = new ArrayList<>();
					países.put(antena.país, markers);
				}
				markers.add(marker);
			}
		}

		final private List<Antena> antenas_temp = new ArrayList<>();
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
			canalSeleccionado(null, null);
			FragmentManager fm = getFragmentManager();
			if(fm.findFragmentByTag("canales") != null)
				fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
			mapa.setPadding(0, altoActionBar, 0, 0);
		}

		@Override
		public boolean onMarkerClick(Marker marker)
		{
			this.marker = marker;

			canalSeleccionado(null, null);

			FragmentManager fm = getFragmentManager();
			CanalesFragment fr = (CanalesFragment)fm.findFragmentByTag("canales");

			Antena antena = markerAAntena.get(marker);
			if(antena.canales == null)
			{
				if(fr != null)
					fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
				mapa.setPadding(0, altoActionBar, 0, 0);
				return false;
			}

			if(fr != null)
			{
				fr = CanalesFragment.crear(antena, this);
				fm.beginTransaction()
						.setCustomAnimations(0, 0, R.anim.canales_enter, R.anim.canales_exit)
						.replace(R.id.bottom_sheet, fr, "canales")
						.addToBackStack("canales")
						.commit();
			} else
			{
				fr = CanalesFragment.crear(antena, this);
				fm.beginTransaction()
					.setCustomAnimations(R.anim.canales_enter, R.anim.canales_exit, R.anim.canales_enter, R.anim.canales_exit)
					.replace(R.id.bottom_sheet, fr, "canales")
					.addToBackStack("canales")
					.commit();
			}
			return false;
		}

		@Override
		public void canalSeleccionado(Antena antena, final Canal canal)
		{
			if(contornoActual != null)
			{
				contornoActual.remove();
				contornoActual = null;
			}
			if(antena == null || antena.país != País.US || canal == null || canal.ref == null)
				return;
			new AsyncTask<Void, Void, Polígono>()
			{
				@Override
				protected Polígono doInBackground(Void... params)
				{
					try
					{
						if(cachéDeContornos == null)
							cachéDeContornos = CachéDeContornos.dameInstancia(getActivity());
						return cachéDeContornos.dameContornoFCC(Integer.parseInt(canal.ref));
					} catch(Exception e)
					{
						Log.e("antenas", "uh?", e);
					}
					return null;
				}

				@Override
				protected void onPostExecute(Polígono polygon)
				{
					PolygonOptions poly = new PolygonOptions();
					poly.addAll(polygon.getPuntos());
					poly.fillColor(ContextCompat.getColor(getActivity(), R.color.contorno));
					contornoActual = mapa.addPolygon(poly);
					CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(polygon.getBoundingBox(), (int)getActivity().getResources().getDimension(R.dimen.paddingContorno));
					mapa.animateCamera(cameraUpdate);
				}
			}.execute();
		}

		@Override
		public void onDestroy()
		{
			if(cachéDeContornos != null)
				cachéDeContornos.devolver();
			super.onDestroy();
		}
	}

	public static class CanalesFragment extends Fragment
	{
		Antena antena;

		interface Callback
		{
			void canalSeleccionado(Antena antena, Canal canal);
		}

		Callback callback;

		static CanalesFragment crear(Antena antena, Callback callback)
		{
			CanalesFragment fr = new CanalesFragment();
			Bundle args = new Bundle();
			args.putInt("país", antena.país.ordinal());
			args.putInt("index", antena.index);
			fr.setArguments(args);
			fr.callback = callback;
			return fr;
		}

		private final View.OnClickListener canalClickListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				v.setSelected(true);
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
			final View v = inflater.inflate(R.layout.info_mapa, container, false);
			TextView tv = (TextView)v.findViewById(R.id.antena_desc);
			if(tv != null)
			{
				if(antena.descripción == null)
					tv.setVisibility(View.GONE);
				else
					tv.setText(antena.descripción);
			}
			ViewGroup l = (ViewGroup)v.findViewById(R.id.lista_canales);
			int n;
			if(l instanceof TableLayout)
			{
				TypedArray arr = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.selectableItemBackground});
				int selectableItemBackground = arr.getResourceId(0, -1);
				arr.recycle();
				n = antena.canales.size();
				int ncolumns = (n + 1) / (antena.descripción != null ? 2 : 3);
				if(ncolumns == 0)
					ncolumns++;
				for(int i = 0; i < (n+ncolumns-1) / ncolumns; i++)
				{
					TableRow row = new TableRow(ctx);

					for(int j = 0 ; j < ncolumns && (i * ncolumns + j) < antena.canales.size() ; j++)
					{
						Canal canal = antena.canales.get(i * ncolumns + j);
						View vc = canal.dameViewCanal(ctx, row, hayImágenes);
						if(antena.país == País.US && canal.ref != null)
						{
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
					l.addView(canal.dameViewCanal(ctx, l, hayImágenes));
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
					v.getViewTreeObserver().removeOnPreDrawListener(this);

					MapaFragment mfr = (MapaFragment)getFragmentManager().findFragmentById(R.id.container);
					//int height = getActivity().findViewById(R.id.bottom_sheet).getHeight();
					int height = v.getHeight();
					height -= ((MapaActivity)getActivity()).publicidad.getHeight();
					if(height < 0)
						height = 0;
					Log.d("antenas", "height=" + height);
					mfr.mapa.setPadding(0, mfr.altoActionBar, 0, height);

					return true;
				}
			});
			return v;
		}
	}
}
