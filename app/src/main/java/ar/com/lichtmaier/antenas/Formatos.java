package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.text.NumberFormat;
import java.util.Locale;

public class Formatos
{
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
		return format(distancia / f) + ' ' + unit;
	}

	public static String formatPower(float potencia)
	{
		return format(potencia) + " kW";
	}

	private static String format(double valor)
	{
		NumberFormat nf = getNumberFormat();
		nf.setMaximumFractionDigits(valor < 1 ? 2 : 1);
		return nf.format(valor);
	}

	private static NumberFormat cachedNumberFormat;
	private static Locale cachedNumberFormatLocale;

	private static NumberFormat getNumberFormat()
	{
		Locale defaultLocale = Locale.getDefault();
		if(!defaultLocale.equals(cachedNumberFormatLocale) || cachedNumberFormat == null)
		{
			Locale locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
					? defaultLocale
					: "es".equals(defaultLocale.getLanguage())
						? new Locale("es", "AR")
						: defaultLocale;
			cachedNumberFormat = NumberFormat.getInstance(locale);
			cachedNumberFormatLocale = defaultLocale;
		}
		return cachedNumberFormat;
	}
}
