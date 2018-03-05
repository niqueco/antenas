package ar.com.lichtmaier.antenas;

import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
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

public class AntenasAdapter extends ListAdapter<AntenasAdapter.AntenaListada, AntenasAdapter.AntenaViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener
{
	final private SharedPreferences prefs;
	private final int resource;
	private final Context context;
	@Nullable final private Brújula brújula;
	private final Callback listener;
	private final Lifecycle lifecycle;
	private Thread threadContornos;
	final private BlockingQueue<Antena> colaParaContornos = new LinkedBlockingQueue<>();
	private boolean todoCargado = false;
	private GlobalCoordinates coordsUsuario;
	private boolean mostrarDireccionesRelativas;
	private boolean forzarDireccionesAbsolutas;

	static class AntenaListada
	{
		final Antena antena;
		double distancia;
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

		AntenaViewHolder(View v)
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

		private void bindTo(AntenaListada al)
		{
			antena = al.antena;

			flechaView.setMostrarPuntosCardinales(!mostrarDireccionesRelativas);

			if(antena.descripción != null)
			{
				tvDesc.setText(antena.descripción);
				if(antena.canales != null && !antena.canales.isEmpty())
					antena.ponéDetalles(tvDetalle);
				else
					tvDetalle.setVisibility(View.GONE);
			} else
			{
				antena.ponéDetalles(tvDesc);
				tvDetalle.setVisibility(View.GONE);
			}
			if(tvPotencia != null)
				tvPotencia.setText(antena.potencia > 0 ? antena.potencia + " kW" : null);
			tvDistancia.setText(Formatos.formatDistance(context, al.distancia));
			avisoLejos.setVisibility(al.lejos ? View.VISIBLE : View.GONE);
			if(!mostrarDireccionesRelativas)
				flechaView.setÁngulo(antena.rumboDesde(coordsUsuario), false);
			else
				suave = false;
		}
	}

	private static final DiffUtil.ItemCallback<AntenaListada> diffCallback = new DiffUtil.ItemCallback<AntenaListada>()
	{
		@Override
		public boolean areItemsTheSame(AntenaListada oldItem, AntenaListada newItem)
		{
			return oldItem.antena.equals(newItem.antena);
		}

		@Override
		public boolean areContentsTheSame(AntenaListada oldItem, AntenaListada newItem)
		{
			return oldItem.lejos == newItem.lejos && Math.abs(oldItem.distancia - newItem.distancia) < (newItem.distancia > 500 ? 100 : 10);
		}
	};

	AntenasAdapter(Context context, @Nullable Brújula brújula, Callback listener, @LayoutRes int resource, Lifecycle lifecycle)
	{
		super(diffCallback);
		this.context = context;
		this.brújula = brújula;
		this.listener = listener;
		this.resource = resource;
		this.lifecycle = lifecycle;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.registerOnSharedPreferenceChangeListener(this);
		setHasStableIds(true);
		mostrarDireccionesRelativas = (brújula != null) && !prefs.getBoolean("forzar_direcciones_absolutas", false);
	}

	@NonNull
	@Override
	public AntenaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
	{
		View v = LayoutInflater.from(parent.getContext()).inflate(resource, parent, false);
		v.setFocusable(true);
		return new AntenaViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull AntenaViewHolder vh, int position)
	{
		vh.bindTo(getItem(position));
	}

	@Override
	public void onViewAttachedToWindow(@NonNull final AntenaViewHolder holder)
	{
		if(brújula != null)
			brújula.registerListener(holder, lifecycle);
	}

	@Override
	public void onViewDetachedFromWindow(@NonNull AntenaViewHolder holder)
	{
		if(brújula != null)
			brújula.removeListener(holder);
	}

	@Override
	public long getItemId(int position)
	{
		Antena antena = getItem(position).antena;
		return (long)antena.país.ordinal() << 60 | antena.index;
	}

	@Override
	public AntenaListada getItem(int position)
	{
		return super.getItem(position);
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
		List<AntenaListada> antenasCerca = new ArrayList<>();
		List<AntenaListada> antenasLejos = new ArrayList<>();
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
			double distancia = a.distanceTo(coordsUsuario);
			if(a.país != País.US || !prefs.getBoolean("usar_contornos", true))
			{
				antenasCerca.add(new AntenaListada(a, distancia, false));
				continue;
			}
			Boolean cerca = cachéCercaníaAntena.get(a);
			if(cerca == null || cerca)
				antenasCerca.add(new AntenaListada(a, distancia, false));
			else
				antenasLejos.add(new AntenaListada(a, distancia, true));

			if(cerca == null || renovarCaché)
			{
				Log.i("antenas", "agregando a la cola a " + a);
				crearThreadContornos();
				colaParaContornos.add(a);
			}
		}
		antenasCerca.addAll(antenasLejos);
		submitList(antenasCerca);
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
