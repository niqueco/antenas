package ar.com.lichtmaier.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import ar.com.lichtmaier.antenas.Formatos;
import ar.com.lichtmaier.antenas.R;

public class DistanceSliderPreference extends DialogPreference
{
	private EditText editText;
	private SeekBar seekBar;

	private static final int MIN_DIST = 2000;
	private static final int MAX_DIST = 500000;
	private double f;
	private int valor;

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
				try {
					moverSeekbar(aMetros(s.toString()));
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
				ponerDistanciaEnEditText(progress + MIN_DIST);
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
	}

	private int aMetros(String str)
	{
		return (int)(Integer.parseInt(str) * f);
	}

	private void ponerDistanciaEnEditText(int metros)
	{
		editText.setText(String.valueOf(Math.round(metros / f)));
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
		if(restoreValue)
		{
			int persistedInt;
			try {
				persistedInt = getPersistedInt(valor);
			} catch(Exception e)
			{
				persistedInt = Integer.parseInt(getPersistedString("60")) * 1000;
				getEditor().remove(getKey()).commit();
			}
			setValue(persistedInt);
		} else
			setValue((Integer)defaultValue);
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
}
