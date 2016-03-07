package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.text.NumberFormat;
import java.util.Locale;

public class Formatos
{
	final static private NumberFormat nf = NumberFormat.getNumberInstance(
			"es".equals(Locale.getDefault().getLanguage())
					? new Locale("es", "AR")
					: Locale.getDefault());

	public static String formatDistance(Context context, double distancia)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String unit = prefs.getString("unit", "km");
		double f;
		switch(unit)
		{
			case "km":
				f = 1000.0;
				break;
			case "mi":
				f = 1609.344;
				break;
			default:
				throw new RuntimeException("unit: " + unit);
		}
		nf.setMaximumFractionDigits(distancia < f ? 2 : 1);
		return nf.format(distancia / f) + ' ' + unit;
	}

	public static String formatPower(float potencia)
	{
		nf.setMaximumFractionDigits(potencia < 1 ? 2 : 1);
		return nf.format(potencia) + " kW";
	}
}
