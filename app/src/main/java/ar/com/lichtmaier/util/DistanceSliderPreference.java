package ar.com.lichtmaier.util;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import ar.com.lichtmaier.antenas.*;

public class DistanceSliderPreference extends DialogPreference
{
	private EditText editText;
	private SeekBar seekBar;

	private static final int MIN_DIST = 4000;
	private static final int MAX_DIST = 400000;
	private double f;
	private int valor;
	private LiveData<List<Antena>> antenasCerca;
	private CantidadDeAntenasEnRadio cantidadDeAntenasEnRadio;
	private TextView cant_antenas;
	private boolean cambiandoEditTextNosotros;

	public DistanceSliderPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setPersistent(true);
		setDialogLayoutResource(R.layout.distance_slider);
	}

	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);

		int current = getPersistedInt(40000);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		String unidad = prefs.getString("unit", "km");

		((TextView)view.findViewById(R.id.tvUnidad)).setText(unidad);

		f = Formatos.unitFactor(unidad);

		editText = view.findViewById(R.id.editText);
		seekBar = view.findViewById(R.id.seekBar);
		cant_antenas = view.findViewById(R.id.cant_antenas);

		editText.setOnClickListener(v -> ((TextView)v).setCursorVisible(true));
		ponerDistanciaEnEditText(current);
		editText.addTextChangedListener(new TextWatcher()
		{
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }

			@Override
			public void afterTextChanged(Editable s)
			{
				if(cambiandoEditTextNosotros)
					return;
				try {
					int m = aMetros(s.toString());
					moverSeekbar(m);
					actualizarCantidadDeAntenas(m);
				} catch(NumberFormatException ignored) { }
			}
		});

		seekBar.setMax(MAX_DIST - MIN_DIST);
		moverSeekbar(current);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
		{
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				if(!fromUser)
					return;
				int m = progress + MIN_DIST;
				ponerDistanciaEnEditText(m);
				actualizarCantidadDeAntenas(m);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				editText.setCursorVisible(false);
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar)
			{

			}
		});

		antenasCerca = Antena.dameAntenasCerca(getContext(), AntenaActivity.coordsUsuario, MAX_DIST, false);
		antenasCerca.observeForever(new Observer<List<Antena>>()
		{
			@Override
			public void onChanged(@Nullable List<Antena> antenas)
			{
				if(antenas == null || AntenaActivity.coordsUsuario == null)
					return;
				antenasCerca.removeObserver(this);
				cantidadDeAntenasEnRadio = new CantidadDeAntenasEnRadio(antenas, AntenaActivity.coordsUsuario);
				actualizarCantidadDeAntenas(seekBar.getProgress() + MIN_DIST);
			}
		});

		((TextView)view.findViewById(R.id.aviso_lejos)).setText(getContext().getResources().getString(R.string.unlikely, unidad.equals("km") ? "80 km" : "50 miles"));
	}

	private void actualizarCantidadDeAntenas(int m)
	{
		if(cantidadDeAntenasEnRadio != null)
		{
			int cant = cantidadDeAntenasEnRadio.en(m);
			cant_antenas.setText(getContext().getResources().getQuantityString(R.plurals.antenas_en_distancia, cant, cant));
		}
	}

	private int aMetros(String str)
	{
		return (int)(Integer.parseInt(str) * f);
	}

	private void ponerDistanciaEnEditText(int metros)
	{
		cambiandoEditTextNosotros = true;
		editText.setText(String.valueOf(Math.round(metros / f)));
		cambiandoEditTextNosotros = false;
	}

	private void moverSeekbar(int m)
	{
		if(m < MIN_DIST || m > MAX_DIST)
			return;
		seekBar.setProgress(m - MIN_DIST);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		if(positiveResult)
		{
			int m = aMetros(editText.getText().toString());
			if(callChangeListener(m))
				setValue(m);
		}
		seekBar = null;
		editText = null;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		return a.getInt(index, 60000);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		setValue(restoreValue ? getPersistedInt(valor) : (Integer)defaultValue);
	}

	private void setValue(int m)
	{
		if(m != valor)
		{
			persistInt(m);
			notifyChanged();
			valor = m;
		}
	}

	private final SharedPreferences.OnSharedPreferenceChangeListener changeListener = (sharedPreferences, key) -> {
		if(key.equals("unit"))
			setSummary(Formatos.formatDistance(getContext(), getPersistedInt(60000)));
	};

	@Override
	protected void onAttachedToActivity()
	{
		super.onAttachedToActivity();
		getSharedPreferences().registerOnSharedPreferenceChangeListener(changeListener);
	}

	@Override
	public void onActivityDestroy()
	{
		super.onActivityDestroy();
		getSharedPreferences().unregisterOnSharedPreferenceChangeListener(changeListener);
	}
}
