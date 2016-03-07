package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AntenasAdapter extends RecyclerView.Adapter<AntenasAdapter.AntenaViewHolder>
{
	final private SharedPreferences prefs;
	List<Antena> antenas, antenasLejos = new ArrayList<>();
	private Context context;
	@Nullable final private Brújula brújula;
	private final Callback antenaClickedListener;
	private Thread threadContornos;
	final private BlockingQueue<Antena> colaParaContornos = new LinkedBlockingQueue<>();

	class AntenaViewHolder extends RecyclerView.ViewHolder implements Brújula.Callback, View.OnClickListener
	{
		private final TextView tvDesc;
		private final TextView tvDetalle;
		private final TextView tvDistancia;
		private final TextView tvPotencia;
		private final FlechaView flechaView;
		private final View avisoLejos;
		private Antena antena;
		private boolean suave;

		public AntenaViewHolder(View v)
		{
			super(v);
			tvDesc = (TextView)v.findViewById(R.id.antena_desc);
			tvDetalle = (TextView)v.findViewById(R.id.antena_detalle_canales);
			tvDistancia = (TextView)v.findViewById(R.id.antena_dist);
			tvPotencia = (TextView)v.findViewById(R.id.antena_potencia);
			flechaView = (FlechaView)v.findViewById(R.id.flecha);
			avisoLejos = v.findViewById(R.id.aviso_lejos);
			v.setOnClickListener(this);
			if(brújula == null)
				flechaView.setMostrarPuntosCardinales(true);
		}

		@Override
		public void nuevaOrientación(double orientación)
		{
			double rumbo = antena.rumboDesde(AntenaActivity.coordsUsuario);
			//Log.d("antenas", "antena: " + antena.descripción + ", rumbo="+ (int)rumbo + ", ángulo flecha="+ (int)(rumbo - orientación));
			flechaView.setÁngulo(rumbo - orientación, suave);
			suave = true;
		}

		@Override
		public void onClick(View v)
		{
			antenaClickedListener.onAntenaClicked(antena, v);
		}
	}

	AntenasAdapter(Context context, @Nullable Brújula brújula, Callback antenaClickedListener)
	{
		this.context = context;
		this.brújula = brújula;
		this.antenaClickedListener = antenaClickedListener;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		setHasStableIds(true);
	}

	@Override
	public AntenaViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.antena, parent, false);
		v.setFocusable(true);
		return new AntenaViewHolder(v);
	}

	@Override
	public void onBindViewHolder(AntenaViewHolder vh, int position)
	{
		Antena a = getAntena(position);
		vh.antena = a;

		CharSequence detalleCanales = a.dameDetalleCanales(context);
		if(a.descripción != null)
		{
			vh.tvDesc.setText(a.descripción);
			if(detalleCanales != null)
				vh.tvDetalle.setText(detalleCanales);
			else
				vh.tvDetalle.setVisibility(View.GONE);
		} else
		{
			vh.tvDesc.setText(detalleCanales);
			vh.tvDetalle.setVisibility(View.GONE);
		}
		if(vh.tvPotencia != null)
			vh.tvPotencia.setText(a.potencia > 0 ? a.potencia + " kW" : null);
		vh.tvDistancia.setText(Formatos.formatDistance(context, a.distanceTo(AntenaActivity.coordsUsuario)));
		vh.avisoLejos.setVisibility(antenasLejos.contains(a) ? View.VISIBLE : View.GONE);
		if(brújula == null)
			vh.flechaView.setÁngulo(a.rumboDesde(AntenaActivity.coordsUsuario), false);
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
	public synchronized int getItemCount()
	{
		return (antenas == null ? 0 : antenas.size()) + antenasLejos.size();
	}

	@Override
	public long getItemId(int position)
	{
		Antena antena = getAntena(position);
		return (long)antena.país.ordinal() << 60 | antena.index;
	}

	private synchronized Antena getAntena(int position)
	{
		int s = antenas.size();
		return position < s ? antenas.get(position) : antenasLejos.get(position - s);
	}

	public void nuevaUbicación(GlobalCoordinates coordsUsuario)
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		List<Antena> antenasCerca = Antena.dameAntenasCerca(context, coordsUsuario,
				maxDist,
				prefs.getBoolean("menos", true));
		synchronized(this)
		{
			for(Antena a : antenasCerca)
			{
				if((antenas == null || !antenas.contains(a)) && !antenasLejos.contains(a))
				{
					if(a.país == País.US && prefs.getBoolean("usar_contornos", true))
					{
						Log.i("antenas", "agregando a la cola a " + a);
						crearThreadContornos();
						colaParaContornos.add(a);
					}
				}
			}
			antenasLejos.retainAll(antenasCerca);
			antenasCerca.removeAll(antenasLejos);
			antenas = antenasCerca;
		}
		notifyDataSetChanged();
	}

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

						if(Log.isLoggable("antenas", Log.DEBUG))
							Log.d("antenas", "buscando contorno para " + antena);

						List<Canal> canalesLejos = null;

						for(Canal c : antena.canales)
						{
							if(c.ref == null)
								continue;

							Polígono polígono = cachéDeContornos.dameContornoFCC(Integer.parseInt(c.ref));

							if(polígono == null)
								continue;

							if(!polígono.contiene(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())))
							{
								if(canalesLejos == null)
									canalesLejos = new ArrayList<>();
								canalesLejos.add(c);
							}
						}

						if(canalesLejos != null)
						{
							if(Log.isLoggable("antenas", Log.DEBUG))
								Log.d("antenas", "La antena " + antena + " tiene canales lejos: " + canalesLejos);
							if(antena.canales.size() == canalesLejos.size())
								bajar(antena);
						}
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

	private void bajar(final Antena antena)
	{
		new Handler(Looper.getMainLooper()).post(new Runnable()
		{
			@Override
			public void run()
			{
				antenas.remove(antena);
				antenasLejos.add(antena);
				notifyDataSetChanged();
			}
		});
	}

	public void onDestroy()
	{
		if(threadContornos != null)
		{
			threadContornos.interrupt();
			threadContornos = null;
		}
	}

	public void reset()
	{
		antenas = null;
		antenasLejos.clear();
		notifyDataSetChanged();
	}

	interface Callback
	{
		void onAntenaClicked(Antena antena, View view);
	}
}
