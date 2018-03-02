package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AntenasAdapter extends RecyclerView.Adapter<AntenasAdapter.AntenaViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener
{
	final private SharedPreferences prefs;
	final List<Antena> antenasCerca = new ArrayList<>();
	final private List<Antena> antenasLejos = new ArrayList<>();
	private final int resource;
	private final Context context;
	@Nullable final private Brújula brújula;
	private final Callback listener;
	private Thread threadContornos;
	final private BlockingQueue<Antena> colaParaContornos = new LinkedBlockingQueue<>();
	private boolean todoCargado = false;
	private GlobalCoordinates coordsUsuario;
	private boolean mostrarDireccionesRelativas;
	private boolean forzarDireccionesAbsolutas;

	class AntenaViewHolder extends RecyclerView.ViewHolder implements Brújula.Callback, View.OnClickListener
	{
		private final CommaEllipsizeTextView tvDesc;
		private final CommaEllipsizeTextView tvDetalle;
		private final TextView tvDistancia;
		private final TextView tvPotencia;
		private final FlechaView flechaView;
		private final View avisoLejos;
		private Antena antena;
		private boolean suave;

		public AntenaViewHolder(View v)
		{
			super(v);
			tvDesc = v.findViewById(R.id.antena_desc);
			tvDetalle = v.findViewById(R.id.antena_detalle_canales);
			tvDistancia = v.findViewById(R.id.antena_dist);
			tvPotencia = v.findViewById(R.id.antena_potencia);
			flechaView = v.findViewById(R.id.flecha);
			avisoLejos = v.findViewById(R.id.aviso_lejos);
			v.setOnClickListener(this);
			flechaView.setMostrarPuntosCardinales(!mostrarDireccionesRelativas);
		}

		@Override
		public void nuevaOrientación(double orientación)
		{
			if(!mostrarDireccionesRelativas)
				return;
			double rumbo = antena.rumboDesde(coordsUsuario);
			//Log.d("antenas", "antena: " + antena.descripción + ", rumbo="+ (int)rumbo + ", ángulo flecha="+ (int)(rumbo - orientación));
			flechaView.setÁngulo(rumbo - orientación, suave);
			suave = true;
		}

		@Override
		public void desorientados()
		{
			if(!mostrarDireccionesRelativas)
				return;
			flechaView.sinValor(suave);
		}

		@Override
		public void faltaCalibrar() { }

		@Override
		public void onClick(View v)
		{
			if(listener != null)
				listener.onAntenaClicked(antena, v);
		}
	}

	AntenasAdapter(Context context, @Nullable Brújula brújula, Callback listener, @LayoutRes int resource)
	{
		this.context = context;
		this.brújula = brújula;
		this.listener = listener;
		this.resource = resource;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.registerOnSharedPreferenceChangeListener(this);
		setHasStableIds(true);
		mostrarDireccionesRelativas = (brújula != null) && !prefs.getBoolean("forzar_direcciones_absolutas", false);
	}

	@Override
	public AntenaViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		View v = LayoutInflater.from(parent.getContext()).inflate(resource, parent, false);
		v.setFocusable(true);
		return new AntenaViewHolder(v);
	}

	@Override
	public void onBindViewHolder(AntenaViewHolder vh, int position)
	{
		Antena a = getAntena(position);
		vh.antena = a;

		vh.flechaView.setMostrarPuntosCardinales(!mostrarDireccionesRelativas);

		if(a.descripción != null)
		{
			vh.tvDesc.setText(a.descripción);
			if(a.canales != null && !a.canales.isEmpty())
				a.ponéDetalles(vh.tvDetalle);
			else
				vh.tvDetalle.setVisibility(View.GONE);
		} else
		{
			a.ponéDetalles(vh.tvDesc);
			vh.tvDetalle.setVisibility(View.GONE);
		}
		if(vh.tvPotencia != null)
			vh.tvPotencia.setText(a.potencia > 0 ? a.potencia + " kW" : null);
		vh.tvDistancia.setText(Formatos.formatDistance(context, a.distanceTo(coordsUsuario)));
		vh.avisoLejos.setVisibility(antenasLejos.contains(a) ? View.VISIBLE : View.GONE);
		if(!mostrarDireccionesRelativas)
			vh.flechaView.setÁngulo(a.rumboDesde(coordsUsuario), false);
		else
			vh.suave = false;
	}

	@Override
	public void onViewAttachedToWindow(final AntenaViewHolder holder)
	{
		if(brújula != null)
			brújula.registerListener(holder);
	}

	@Override
	public void onViewDetachedFromWindow(AntenaViewHolder holder)
	{
		if(brújula != null)
			brújula.removeListener(holder);
	}

	@Override
	public int getItemCount()
	{
		return (antenasCerca == null ? 0 : antenasCerca.size()) + antenasLejos.size();
	}

	@Override
	public long getItemId(int position)
	{
		Antena antena = getAntena(position);
		return (long)antena.país.ordinal() << 60 | antena.index;
	}

	private Antena getAntena(int position)
	{
		int s = antenasCerca.size();
		return position < s ? antenasCerca.get(position) : antenasLejos.get(position - s);
	}

	private void refrescar()
	{
		if(coordsUsuario != null)
			nuevaUbicación(coordsUsuario);
	}

	public void nuevaUbicación(GlobalCoordinates coordsUsuario)
	{
		this.coordsUsuario = coordsUsuario;
		if(llamarANuevaUbicación != null)
			llamarANuevaUbicación.removeMessages(0);
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		List<Antena> antenasAlrededor;
		try
		{
			antenasAlrededor = Antena.dameAntenasCerca(context, coordsUsuario,
					maxDist,
					prefs.getBoolean("menos", true));
		} catch(TimeoutException e)
		{
			Log.d("antenas", "Las antenas no están cargadas, probamos más tarde.");
			crearHandler();
			llamarANuevaUbicación.sendEmptyMessageDelayed(0, 100);
			return;
		}
		Log.d("antenas", "Tengo " + antenasAlrededor.size() + " antenas alrededor.");
		antenasCerca.clear();
		antenasLejos.clear();
		LatLng coords = new LatLng(coordsUsuario.getLatitude(), coordsUsuario.getLongitude());
		boolean renovarCaché = false;
		if(posCachéCercanía != null)
		{
			if(SphericalUtil.computeDistanceBetween(posCachéCercanía, coords) > 200)
			{
				if(Log.isLoggable("antenas", Log.DEBUG))
					Log.d("antenas", "Renuevo caché de cercanía de " + cachéCercaníaAntena.size() + " elementos.");
				renovarCaché = true;
				posCachéCercanía = coords;
				cachéCercaníaAntena.keySet().retainAll(antenasAlrededor);
			}
		} else
		{
			posCachéCercanía = coords;
		}
		for(Antena a : antenasAlrededor)
		{
			if(a.país != País.US || !prefs.getBoolean("usar_contornos", true))
			{
				antenasCerca.add(a);
				continue;
			}
			Boolean cerca = cachéCercaníaAntena.get(a);
			if(cerca == null || cerca)
				antenasCerca.add(a);
			else
				antenasLejos.add(a);

			if(cerca == null || renovarCaché)
			{
				Log.i("antenas", "agregando a la cola a " + a);
				crearThreadContornos();
				colaParaContornos.add(a);
			}
		}
		notifyDataSetChanged();
		if(!todoCargado)
		{
			todoCargado = true;
			if(listener != null)
				listener.onAdapterReady();
		}
	}

	public void setForzarDireccionesAbsolutas(boolean forzarDireccionesAbsolutas)
	{
		this.forzarDireccionesAbsolutas = forzarDireccionesAbsolutas;
		configurarMostrarDireccionesRelativas();
	}

	private void configurarMostrarDireccionesRelativas()
	{
		boolean m = (brújula != null) && !prefs.getBoolean("forzar_direcciones_absolutas", false) && !forzarDireccionesAbsolutas;
		if(m == this.mostrarDireccionesRelativas)
			return;
		this.mostrarDireccionesRelativas = m;
		notifyDataSetChanged();
	}

	final private Map<Antena,Boolean> cachéCercaníaAntena = new HashMap<>();
	private LatLng posCachéCercanía;
	private Handler llamarANuevaUbicación;

	private synchronized void crearThreadContornos()
	{
		if(threadContornos != null)
			return;
		threadContornos = new Thread("antenas-contornos") {

			private CachéDeContornos cachéDeContornos;

			@Override
			public void run()
			{
				cachéDeContornos = CachéDeContornos.dameInstancia(context);
				try
				{
					//noinspection InfiniteLoopStatement
					while(true)
					{
						final Antena antena = colaParaContornos.poll(15, TimeUnit.SECONDS);

						if(antena == null)
							break;

						final boolean cerca = cachéDeContornos.enContorno(antena, new LatLng(coordsUsuario.getLatitude(), coordsUsuario.getLongitude()), true);
						new Handler(Looper.getMainLooper()).post(() -> {
							cachéCercaníaAntena.put(antena, cerca);
							crearHandler();
							llamarANuevaUbicación.sendEmptyMessageDelayed(0, 2000);
						});
					}
				} catch(InterruptedException ignored) { }
				finally
				{
					cachéDeContornos.devolver();
					synchronized(AntenasAdapter.this)
					{
						threadContornos = null;
					}
				}
			}
		};
		threadContornos.start();
	}

	private void crearHandler()
	{
		if(llamarANuevaUbicación == null)
			llamarANuevaUbicación = new LlamarANuevaUbicación(this);
	}

	public void onDestroy()
	{
		if(threadContornos != null)
		{
			threadContornos.interrupt();
			threadContornos = null;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		switch(key)
		{
			case "forzar_direcciones_absolutas":
				configurarMostrarDireccionesRelativas();
				break;
			case "usar_contornos":
				cachéCercaníaAntena.clear();
			case "max_dist":
				refrescar();
				break;
		}
	}

	interface Callback
	{
		void onAntenaClicked(Antena antena, View view);

		void onAdapterReady();
	}

	private static class LlamarANuevaUbicación extends Handler
	{
		private final WeakReference<AntenasAdapter> antenasAdapterWeakReference;

		LlamarANuevaUbicación(AntenasAdapter antenasAdapter)
		{
			antenasAdapterWeakReference = new WeakReference<>(antenasAdapter);
		}

		@Override
		public void handleMessage(Message msg)
		{
			AntenasAdapter antenasAdapter = antenasAdapterWeakReference.get();
			if(antenasAdapter != null)
				antenasAdapter.refrescar();
		}
	}
}
