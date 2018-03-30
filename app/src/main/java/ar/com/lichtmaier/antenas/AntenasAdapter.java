package ar.com.lichtmaier.antenas;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.gavaghan.geodesy.GlobalCoordinates;

import ar.com.lichtmaier.antenas.AntenasRepository.AntenaListada;

public class AntenasAdapter extends ListAdapter<AntenaListada, AntenasAdapter.AntenaViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener
{
	final private SharedPreferences prefs;
	private final int resource;
	private final Context context;
	@Nullable final private Brújula brújula;
	private final Callback listener;
	private final Lifecycle lifecycle;
	private final LiveData<Location> location;
	private GlobalCoordinates coords;
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
			Location loc = location.getValue();
			if(loc == null)
				throw new NullPointerException();
			double rumbo = antena.rumboDesde(coords);
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
				flechaView.setÁngulo(antena.rumboDesde(coords), false);
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

	AntenasAdapter(Context context, @Nullable Brújula brújula, LiveData<Location> location, Callback listener, @LayoutRes int resource, LifecycleOwner lifecycleOwner)
	{
		super(diffCallback);
		this.context = context;
		this.brújula = brújula;
		this.location = location;
		this.listener = listener;
		this.resource = resource;
		this.lifecycle = lifecycleOwner.getLifecycle();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.registerOnSharedPreferenceChangeListener(this);
		setHasStableIds(true);
		mostrarDireccionesRelativas = (brújula != null) && !prefs.getBoolean("forzar_direcciones_absolutas", false);
		location.observe(lifecycleOwner, loc -> {
			if(loc != null)
				coords = new GlobalCoordinates(loc.getLatitude(), loc.getLongitude());
		});
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if("forzar_direcciones_absolutas".equals(key))
			configurarMostrarDireccionesRelativas();
	}

	interface Callback
	{
		void onAntenaClicked(Antena antena, View view);
	}
}
